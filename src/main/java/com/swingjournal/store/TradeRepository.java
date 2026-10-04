package com.swingjournal.store;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Trade;

/**
 * SQLite storage. Prices and quantities are stored as TEXT so BigDecimal values round-trip
 * exactly; dates are stored as ISO-8601 text.
 */
@Repository
public class TradeRepository {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS trade (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol        TEXT NOT NULL,
                company_name  TEXT,
                trade_date    TEXT NOT NULL,
                entry_price   TEXT NOT NULL,
                quantity      TEXT NOT NULL,
                entry_reason  TEXT,
                planned_stop  TEXT NOT NULL,
                stop_reason   TEXT,
                planned_exit  TEXT NOT NULL,
                exit_reason   TEXT,
                exit_price    TEXT,
                exit_date     TEXT,
                exit_type     TEXT,
                lesson        TEXT
            )""";

    // Re-importing the same CSV must not create duplicates.
    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_trade_natural ON trade (symbol, trade_date, entry_price)";

    private static final String INSERT = """
            INSERT OR IGNORE INTO trade (symbol, company_name, trade_date, entry_price, quantity, entry_reason,
                planned_stop, stop_reason, planned_exit, exit_reason, exit_price, exit_date, exit_type, lesson)
            VALUES (:symbol, :company, :tradeDate, :entry, :qty, :entryReason,
                :stop, :stopReason, :target, :exitReason, :exitPrice, :exitDate, :exitType, :lesson)""";

    private final JdbcClient jdbc;

    public TradeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void createSchema() {
        jdbc.sql(DDL).update();
        jdbc.sql(UNIQUE_INDEX).update();
    }

    public List<Trade> findAll() {
        return jdbc.sql("SELECT * FROM trade ORDER BY trade_date DESC, id DESC")
                .query(TradeRepository::map).list();
    }

    public Optional<Trade> find(long id) {
        return jdbc.sql("SELECT * FROM trade WHERE id = :id").param("id", id)
                .query(TradeRepository::map).optional();
    }

    /** Inserts the trade; returns empty if an identical symbol/date/entry price already exists. */
    public Optional<Trade> insert(Trade t) {
        KeyHolder keys = new GeneratedKeyHolder();
        int rows = jdbc.sql(INSERT)
                .param("symbol", t.symbol())
                .param("company", t.companyName())
                .param("tradeDate", t.tradeDate().toString())
                .param("entry", t.entryPrice().toPlainString())
                .param("qty", t.quantity().toPlainString())
                .param("entryReason", t.entryReason())
                .param("stop", t.plannedStop().toPlainString())
                .param("stopReason", t.stopReason())
                .param("target", t.plannedExit().toPlainString())
                .param("exitReason", t.exitReason())
                .param("exitPrice", t.exitPrice() == null ? null : t.exitPrice().toPlainString())
                .param("exitDate", t.exitDate() == null ? null : t.exitDate().toString())
                .param("exitType", t.exitType() == null ? null : t.exitType().name())
                .param("lesson", t.lesson())
                .update(keys);
        if (rows == 0 || keys.getKey() == null) {
            return Optional.empty();
        }
        return Optional.of(t.withId(keys.getKey().longValue()));
    }

    /** Records the exit; returns false if the trade doesn't exist. */
    public boolean close(long id, BigDecimal exitPrice, LocalDate exitDate, ExitType type, String lesson) {
        return jdbc.sql("""
                UPDATE trade SET exit_price = :price, exit_date = :date, exit_type = :type,
                                 lesson = COALESCE(:lesson, lesson)
                WHERE id = :id""")
                .param("price", exitPrice.toPlainString())
                .param("date", exitDate.toString())
                .param("type", type.name())
                .param("lesson", lesson)
                .param("id", id)
                .update() > 0;
    }

    private static Trade map(ResultSet rs, int row) throws SQLException {
        return new Trade(
                rs.getLong("id"),
                rs.getString("symbol"),
                rs.getString("company_name"),
                LocalDate.parse(rs.getString("trade_date")),
                new BigDecimal(rs.getString("entry_price")),
                new BigDecimal(rs.getString("quantity")),
                rs.getString("entry_reason"),
                new BigDecimal(rs.getString("planned_stop")),
                rs.getString("stop_reason"),
                new BigDecimal(rs.getString("planned_exit")),
                rs.getString("exit_reason"),
                decimal(rs.getString("exit_price")),
                rs.getString("exit_date") == null ? null : LocalDate.parse(rs.getString("exit_date")),
                rs.getString("exit_type") == null ? null : ExitType.valueOf(rs.getString("exit_type")),
                rs.getString("lesson"));
    }

    private static BigDecimal decimal(String s) {
        return s == null ? null : new BigDecimal(s);
    }
}
