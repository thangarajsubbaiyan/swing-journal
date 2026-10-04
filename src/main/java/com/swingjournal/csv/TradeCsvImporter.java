package com.swingjournal.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Trade;

/**
 * Reads the CSV exported from the old "Swing trade" spreadsheet.
 * Required: Symbol, Trade date, Entry price, Quantity, Planned Stop loss, Planned Exit price.
 * Everything else is optional. Derived columns (Amount invested, Risk/Reward ratio, Profit/Loss,
 * Success/Failure) and the duplicate "Strategy" column are ignored; the app recalculates them.
 */
public final class TradeCsvImporter {

    public record ParseResult(List<Trade> trades, List<String> errors) {
    }

    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");
    // "CMG - Chipotle", "TT- Trane"; a hyphen only splits when whitespace follows it (BRK-B stays whole)
    private static final Pattern SYMBOL_AND_NAME = Pattern.compile("^([A-Za-z0-9.\\-]+?)\\s*-\\s+(.+)$");

    private TradeCsvImporter() {
    }

    public static ParseResult parse(String csvText) {
        List<List<String>> rows = CsvParser.parse(csvText);
        List<Trade> trades = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            errors.add("The file is empty.");
            return new ParseResult(trades, errors);
        }

        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(normalize(header.get(i)), i);
        }
        for (String required : List.of("symbol", "trade date", "entry price", "quantity",
                "planned stop loss", "planned exit price")) {
            if (!col.containsKey(required)) {
                errors.add("Missing required column: " + required);
            }
        }
        if (!errors.isEmpty()) {
            return new ParseResult(trades, errors);
        }

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(s -> s.isBlank())) {
                continue;
            }
            int line = r + 1;
            try {
                trades.add(toTrade(row, col));
            } catch (RuntimeException e) {
                errors.add("Row " + line + ": " + e.getMessage());
            }
        }
        return new ParseResult(trades, errors);
    }

    private static Trade toTrade(List<String> row, Map<String, Integer> col) {
        String rawSymbol = text(row, col, "symbol");
        if (rawSymbol == null) {
            throw new IllegalArgumentException("Symbol is empty");
        }
        String symbol = rawSymbol;
        String company = null;
        Matcher m = SYMBOL_AND_NAME.matcher(rawSymbol);
        if (m.matches()) {
            symbol = m.group(1);
            company = m.group(2).trim();
        }

        BigDecimal entry = requiredNumber(row, col, "entry price");
        BigDecimal exitPrice = number(row, col, "executed exit price");
        LocalDate exitDate = date(row, col, "exit date");
        ExitType exitType = null;
        BigDecimal plannedExit = requiredNumber(row, col, "planned exit price");

        if (exitPrice != null) {
            exitType = exitPrice.compareTo(plannedExit) >= 0 ? ExitType.TARGET : ExitType.MANUAL;
        } else {
            BigDecimal stopped = number(row, col, "executed stop loss price");
            if (stopped != null) {
                exitPrice = stopped;
                exitType = ExitType.STOP;
                exitDate = date(row, col, "stop loss executed date");
            }
        }

        return new Trade(null, symbol.toUpperCase(Locale.ROOT), company,
                requiredDate(row, col, "trade date"), entry,
                requiredNumber(row, col, "quantity"),
                text(row, col, "entry price strategy"),
                requiredNumber(row, col, "planned stop loss"),
                text(row, col, "stop loss strategy"),
                plannedExit,
                text(row, col, "exit price strategy"),
                exitPrice, exitDate, exitType,
                text(row, col, "lesson learned"));
    }

    private static String normalize(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.size()) {
            return null;
        }
        String v = row.get(i).replace("\r\n", "\n").replace('\r', '\n').replace(' ', '\n').trim();
        return v.isEmpty() ? null : v;
    }

    private static BigDecimal number(List<String> row, Map<String, Integer> col, String name) {
        String v = text(row, col, name);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace("$", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + name + "' is not a number: " + v);
        }
    }

    private static BigDecimal requiredNumber(List<String> row, Map<String, Integer> col, String name) {
        BigDecimal v = number(row, col, name);
        if (v == null) {
            throw new IllegalArgumentException("'" + name + "' is required");
        }
        return v;
    }

    private static LocalDate date(List<String> row, Map<String, Integer> col, String name) {
        String v = text(row, col, name);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v, US_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + name + "' is not a date (expected M/d/yyyy): " + v);
        }
    }

    private static LocalDate requiredDate(List<String> row, Map<String, Integer> col, String name) {
        LocalDate v = date(row, col, name);
        if (v == null) {
            throw new IllegalArgumentException("'" + name + "' is required");
        }
        return v;
    }
}
