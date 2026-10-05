package com.swingjournal.store;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Trade;

/**
 * SQLite storage. Prices and quantities are stored as TEXT so BigDecimal values round-trip
 * exactly; dates are stored as ISO-8601 text. trade_date and quantity are nullable because a
 * trade can be planned without ever being executed.
 */
@Repository
public class TradeRepository {

    private static final String COLUMNS = """
            id, symbol, company_name, trade_date, entry_price, quantity, entry_reason, planned_stop, stop_reason,
            planned_exit, exit_reason, exit_price, exit_date, exit_type, lesson""";

    private static String createTable(String name) {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol        TEXT NOT NULL,
                company_name  TEXT,
                trade_date    TEXT,
                entry_price   TEXT NOT NULL,
                quantity      TEXT,
                entry_reason  TEXT,
                planned_stop  TEXT NOT NULL,
                stop_reason   TEXT,
                planned_exit  TEXT NOT NULL,
                exit_reason   TEXT,
                exit_price    TEXT,
                exit_date     TEXT,
                exit_type     TEXT,
                lesson        TEXT
            )""".formatted(name);
    }

    // Re-importing the same CSV must not create duplicates (planned trades without a date are not deduplicated).
    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_trade_natural ON trade (symbol, trade_date, entry_price)";

    private static final String INSERT = """
            INSERT OR IGNORE INTO trade (symbol, company_name, trade_date, entry_price, quantity, entry_reason,
                planned_stop, stop_reason, planned_exit, exit_reason, exit_price, exit_date, exit_type, lesson)
            VALUES (:symbol, :company, :tradeDate, :entry, :qty, :entryReason,
                :stop, :stopReason, :target, :exitReason, :exitPrice, :exitDate, :exitType, :lesson)""";

    private static final String UPDATE = """
            UPDATE trade SET symbol = :symbol, company_name = :company, trade_date = :tradeDate,
                entry_price = :entry, quantity = :qty, entry_reason = :entryReason, planned_stop = :stop,
                stop_reason = :stopReason, planned_exit = :target, exit_reason = :exitReason,
                exit_price = :exitPrice, exit_date = :exitDate, exit_type = :exitType, lesson = :lesson
            WHERE id = :id""";

    private final JdbcClient jdbc;
    private final DataSource dataSource;

    public TradeRepository(JdbcClient jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void createSchema() throws SQLException {
        jdbc.sql(createTable("trade")).update();
        if (tradeDateIsRequired()) {
            migrateToOptionalDateAndQuantity();
        }
        jdbc.sql(UNIQUE_INDEX).update();
    }

    /** Version 0.1 of the app created trade_date and quantity as NOT NULL. */
    private boolean tradeDateIsRequired() {
        return jdbc.sql("SELECT \"notnull\" FROM pragma_table_info('trade') WHERE name = 'trade_date'")
                .query(Integer.class).optional().orElse(0) == 1;
    }

    /** Rebuilds the table with nullable trade_date and quantity, keeping every row. All-or-nothing. */
    private void migrateToOptionalDateAndQuantity() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                s.execute("DROP INDEX IF EXISTS ux_trade_natural");
                s.execute("ALTER TABLE trade RENAME TO trade_old");
                s.execute(createTable("trade"));
                s.execute("INSERT INTO trade (" + COLUMNS + ") SELECT " + COLUMNS + " FROM trade_old");
                s.execute("DROP TABLE trade_old");
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<Trade> findAll() {
        // Planned trades (no date yet) first, then newest first.
        return jdbc.sql("SELECT * FROM trade ORDER BY (trade_date IS NULL) DESC, trade_date DESC, id DESC")
                .query(TradeRepository::map).list();
    }

    public Optional<Trade> find(long id) {
        return jdbc.sql("SELECT * FROM trade WHERE id = :id").param("id", id)
                .query(TradeRepository::map).optional();
    }

    /** Inserts the trade; returns empty if an identical symbol/date/entry price already exists. */
    public Optional<Trade> insert(Trade t) {
        KeyHolder keys = new GeneratedKeyHolder();
        int rows = bind(jdbc.sql(INSERT), t).update(keys);
        if (rows == 0 || keys.getKey() == null) {
            return Optional.empty();
        }
        return Optional.of(t.withId(keys.getKey().longValue()));
    }

    /** Overwrites all editable fields of trade {@code id}; returns false if it doesn't exist. */
    public boolean update(long id, Trade t) {
        return bind(jdbc.sql(UPDATE), t).param("id", id).update() > 0;
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM trade WHERE id = :id").param("id", id).update() > 0;
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec s, Trade t) {
        return s.param("symbol", t.symbol())
                .param("company", t.companyName())
                .param("tradeDate", text(t.tradeDate()))
                .param("entry", text(t.entryPrice()))
                .param("qty", text(t.quantity()))
                .param("entryReason", t.entryReason())
                .param("stop", text(t.plannedStop()))
                .param("stopReason", t.stopReason())
                .param("target", text(t.plannedExit()))
                .param("exitReason", t.exitReason())
                .param("exitPrice", text(t.exitPrice()))
                .param("exitDate", text(t.exitDate()))
                .param("exitType", t.exitType() == null ? null : t.exitType().name())
                .param("lesson", t.lesson());
    }

    private static String text(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String text(LocalDate v) {
        return v == null ? null : v.toString();
    }

    private static Trade map(ResultSet rs, int row) throws SQLException {
        return new Trade(
                rs.getLong("id"),
                rs.getString("symbol"),
                rs.getString("company_name"),
                date(rs.getString("trade_date")),
                decimal(rs.getString("entry_price")),
                decimal(rs.getString("quantity")),
                rs.getString("entry_reason"),
                decimal(rs.getString("planned_stop")),
                rs.getString("stop_reason"),
                decimal(rs.getString("planned_exit")),
                rs.getString("exit_reason"),
                decimal(rs.getString("exit_price")),
                date(rs.getString("exit_date")),
                rs.getString("exit_type") == null ? null : ExitType.valueOf(rs.getString("exit_type")),
                rs.getString("lesson"));
    }

    private static BigDecimal decimal(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static LocalDate date(String s) {
        return s == null ? null : LocalDate.parse(s);
    }
}
