package com.swingjournal.store;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.swingjournal.domain.Setup;

/** Stores the user's chart-setup playbook in the same SQLite file as the trades. */
@Repository
public class SetupRepository {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS setup (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT NOT NULL UNIQUE,
                description   TEXT,
                trading_notes TEXT,
                checklist     TEXT,
                warnings      TEXT
            )""";

    private static final String NO_EVENTS = "No earnings or stock split inside the holding period";

    private final JdbcClient jdbc;

    public SetupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void createSchemaAndSeed() {
        jdbc.sql(DDL).update();
        int count = jdbc.sql("SELECT COUNT(*) FROM setup").query(Integer.class).single();
        if (count == 0) {
            seed();
        }
    }

    public List<Setup> findAll() {
        return jdbc.sql("SELECT * FROM setup ORDER BY name COLLATE NOCASE")
                .query((rs, i) -> new Setup(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("trading_notes"), rs.getString("checklist"), rs.getString("warnings")))
                .list();
    }

    public Optional<Setup> find(long id) {
        return findAll().stream().filter(s -> s.id() == id).findFirst();
    }

    public Setup insert(Setup s) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO setup (name, description, trading_notes, checklist, warnings)
                VALUES (:name, :description, :notes, :checklist, :warnings)""")
                .param("name", s.name()).param("description", s.description()).param("notes", s.tradingNotes())
                .param("checklist", s.checklist()).param("warnings", s.warnings())
                .update(keys);
        return new Setup(keys.getKey().longValue(), s.name(), s.description(), s.tradingNotes(),
                s.checklist(), s.warnings());
    }

    public boolean update(long id, Setup s) {
        return jdbc.sql("""
                UPDATE setup SET name = :name, description = :description, trading_notes = :notes,
                                 checklist = :checklist, warnings = :warnings
                WHERE id = :id""")
                .param("name", s.name()).param("description", s.description()).param("notes", s.tradingNotes())
                .param("checklist", s.checklist()).param("warnings", s.warnings()).param("id", id)
                .update() > 0;
    }

    /** Deletes the setup; trades that used it keep existing but lose the link. */
    public boolean delete(long id) {
        jdbc.sql("UPDATE trade SET setup_id = NULL, checklist_done = NULL WHERE setup_id = :id")
                .param("id", id).update();
        return jdbc.sql("DELETE FROM setup WHERE id = :id").param("id", id).update() > 0;
    }

    private void seed() {
        insert(new Setup(null, "Channel up",
                "Price rises between two parallel, upward-sloping trendlines: higher highs and higher lows. "
                        + "The uptrend is intact while price keeps respecting both lines.",
                "Buy near the lower line after a bounce. Stop just below the lower line or about $1 below the 50 EMA. "
                        + "Target the upper line.",
                String.join("\n",
                        "At least two touches on each trendline",
                        "Entry is near the lower line, after a bounce",
                        "Price is above a rising 50 EMA",
                        "Volume is higher on up-moves than on pullbacks",
                        NO_EVENTS),
                "A close below the lower line or below the 50 EMA means the uptrend is weakening. "
                        + "Very steep channels tend to break sooner."));
        insert(new Setup(null, "Ascending triangle",
                "Flat resistance on top and rising lows underneath, squeezing price toward the resistance. "
                        + "Usually a bullish continuation pattern.",
                "Enter on a close above the resistance (or on a retest of it). Stop below the latest higher low, "
                        + "or back inside the triangle. Target: the triangle's height added to the breakout point.",
                String.join("\n",
                        "Flat resistance tested at least twice",
                        "Higher lows pressing up into the resistance",
                        "Price closes above the resistance",
                        "Breakout volume is above average",
                        NO_EVENTS),
                "A close back below the resistance after the breakout is a failed breakout. "
                        + "Breakouts on low volume fail more often."));
        insert(new Setup(null, "Wedge down",
                "Price falls between two converging, downward-sloping lines. Selling pressure fades as the wedge "
                        + "narrows, and a break upward is often a bullish reversal.",
                "Enter on a close above the upper line. Stop below the wedge's last low. A common target is "
                        + "where the wedge started (its widest point).",
                String.join("\n",
                        "Both lines slope down and converge",
                        "At least two touches on each line",
                        "Volume dries up as the wedge narrows",
                        "Price closes above the upper line",
                        NO_EVENTS),
                "A close below the lower line means the wedge failed. "
                        + "Breakouts without volume often fail."));
        insert(new Setup(null, "Double support",
                "Price tests the same support level twice (or more) and bounces, showing buyers defend that price.",
                "Enter after a bounce from the latest test, or on a close above the peak between the two lows. "
                        + "Stop just below the support zone. Target: the recent high.",
                String.join("\n",
                        "Support tested at least twice at about the same price",
                        "Price bounced from the latest test",
                        "Stop fits just below the support zone",
                        NO_EVENTS),
                "A close below the support zone means the level failed. A third test often fails."));
    }
}
