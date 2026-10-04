package com.swingjournal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One long swing trade. The plan fields are filled in at entry; exitPrice, exitDate,
 * exitType and lesson stay null until the trade is closed. Everything else
 * (amount invested, risk/reward, P/L...) is calculated in {@link TradeMetrics}.
 */
public record Trade(
        Long id,
        String symbol,
        String companyName,
        LocalDate tradeDate,
        BigDecimal entryPrice,
        BigDecimal quantity,
        String entryReason,
        BigDecimal plannedStop,
        String stopReason,
        BigDecimal plannedExit,
        String exitReason,
        BigDecimal exitPrice,
        LocalDate exitDate,
        ExitType exitType,
        String lesson) {

    public boolean isClosed() {
        return exitPrice != null;
    }

    public Trade withId(long newId) {
        return new Trade(newId, symbol, companyName, tradeDate, entryPrice, quantity, entryReason,
                plannedStop, stopReason, plannedExit, exitReason, exitPrice, exitDate, exitType, lesson);
    }
}
