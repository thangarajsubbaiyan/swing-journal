# Swing Journal

A personal swing-trade journal. Plan each trade (entry, stop, target and the reasons), close it later,
and let the app calculate amount invested, risk/reward, risk %, P/L and R-multiple.

## Run

Requires JDK 21+ and Maven.

    mvn spring-boot:run

Open http://localhost:8080

## Where the data lives

A SQLite file at `~/swing-journal/journal.db`, outside the project, so rebuilding never touches it.
To use another folder: `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djournal.db.dir=/some/folder"`.
Back up by copying the file. Inspect it with `sqlite3 ~/swing-journal/journal.db` (already on macOS).

## CSV import

Use the page's "Import from CSV" box, or:

    curl -F file=@Swing_trade.csv http://localhost:8080/api/import

Required columns: Symbol, Trade date, Entry price, Quantity, Planned Stop loss, Planned Exit price.
Everything else is optional. Amount invested, Risk/Reward ratio, Profit/Loss, Success/Failure and the
duplicate Strategy column are ignored because the app recalculates them. Re-importing skips duplicates
(same symbol, date and entry price).

## Scope of version 0.1

Long trades only. No accounts, no charts, no live prices.
