# Swing Journal

A personal swing-trade journal. Each trade moves through three phases, and you can edit it at any point:

1. **Plan** – symbol, entry, stop, target and the reasons. No date or quantity needed.
2. **Execute** – add the trade date and quantity (and the actual entry price if it differed).
3. **Result** – exit price and date, plus how it went. Profit or loss, risk/reward, risk % and R-multiple
   are calculated for you.

A planned trade doesn't have to be executed. Delete it if you decide to skip it.

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

Required columns: Symbol, Entry price, Planned Stop loss, Planned Exit price.
Everything else is optional; a row without a trade date or quantity is imported as a planned trade. Amount invested, Risk/Reward ratio, Profit/Loss, Success/Failure and the
duplicate Strategy column are ignored because the app recalculates them. Re-importing skips duplicates
(same symbol, date and entry price).

## Upgrading from 0.1

The first start of this version rebuilds the `trade` table so trade date and quantity can be empty.
All existing rows are kept (the change is all-or-nothing). Copy `~/swing-journal/journal.db` somewhere first
if you want a backup.

## Scope

Long trades only. No accounts, no charts, no live prices.
