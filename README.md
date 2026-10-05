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

## Estimate: max loss and max profit

In the Plan section, enter the entry, stop and target, then the **capital** you want to put into the trade.
The app shows the number of shares (whole shares by default; tick "Allow fractional shares" to use the full amount),
the amount invested, cash left, **max loss** and **max profit** in dollars and as a percent of your capital, and
reward:risk. "Use this quantity" copies the share count into the trade. Your last capital value is remembered
in the browser.

Max loss assumes the stop fills exactly at the stop price. Gaps or fast moves can make the real loss bigger, and
fees are not included. The same numbers are available at `GET /api/estimate?entry=&stop=&target=&capital=&fractional=`.

## Setups

The **Setups library** is your chart playbook. It starts with four cards (Channel up, Ascending triangle,
Wedge down, Double support), each with what it is, how to trade it, a checklist and what breaks it.
The starter text is general education: edit it to match how you trade, or add your own setups.

Pick a setup in the Plan section of a trade to see its card and tick its checklist. The ticks are saved with the
trade, and the table shows how many were ticked (for example `Channel up 4/5`). Deleting a setup keeps your trades
but removes their link to it.

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

## Upgrading from earlier versions

The first start of a newer version upgrades the database in place: it lets trade date and quantity be empty
(rebuilding the `trade` table; all rows are kept, all-or-nothing) and adds the setup columns. Copy `~/swing-journal/journal.db` somewhere first
if you want a backup.

## Scope

Long trades only. No accounts, no charts, no live prices.
