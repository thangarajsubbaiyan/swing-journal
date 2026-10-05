package com.swingjournal.domain;

import java.math.BigDecimal;

/** Rules that depend on how far along a trade is. Throws IllegalArgumentException with a readable message. */
public final class TradeValidator {

    private TradeValidator() {
    }

    public static void validate(Trade t) {
        require(t.symbol() != null && !t.symbol().isBlank(), "symbol is required");
        require(positive(t.entryPrice()), "entry price must be greater than 0");
        require(positive(t.plannedStop()), "planned stop must be greater than 0");
        require(positive(t.plannedExit()), "planned exit must be greater than 0");
        require(t.plannedStop().compareTo(t.entryPrice()) < 0,
                "planned stop must be below the entry price (long trades only)");
        require(t.plannedExit().compareTo(t.entryPrice()) > 0,
                "planned exit must be above the entry price (long trades only)");

        if (t.quantity() != null) {
            require(positive(t.quantity()), "quantity must be greater than 0");
        }
        if (t.tradeDate() != null) {
            require(t.quantity() != null, "an executed trade needs a quantity");
        }

        if (t.exitPrice() != null) {
            require(positive(t.exitPrice()), "exit price must be greater than 0");
            require(t.tradeDate() != null, "execute the trade (trade date and quantity) before recording a result");
            require(t.exitDate() != null, "exit date is required when there is an exit price");
            require(!t.exitDate().isBefore(t.tradeDate()), "exit date can't be before the trade date");
        } else {
            require(t.exitDate() == null && t.exitType() == null, "exit date or type was given without an exit price");
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
