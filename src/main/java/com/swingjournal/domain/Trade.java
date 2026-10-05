package com.swingjournal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One long swing trade, filled in over three phases:
 * <ol>
 *   <li>Plan: symbol, entry price, planned stop and target, and the reasons. No date or quantity needed.</li>
 *   <li>Execute: tradeDate and quantity (and entryPrice updated to the actual fill, if different).</li>
 *   <li>Result: exitPrice, exitDate, exitType and lesson.</li>
 * </ol>
 * A planned trade may never be executed. Calculated values live in {@link TradeMetrics}.
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

    public boolean isExecuted() {
        return tradeDate != null;
    }

    public boolean isClosed() {
        return exitPrice != null;
    }

    public Trade withId(long newId) {
        return new Trade(newId, symbol, companyName, tradeDate, entryPrice, quantity, entryReason,
                plannedStop, stopReason, plannedExit, exitReason, exitPrice, exitDate, exitType, lesson);
    }

    /** Records the execution of a planned trade. */
    public Trade withExecution(LocalDate date, BigDecimal actualEntry, BigDecimal qty) {
        return new Trade(id, symbol, companyName, date, actualEntry != null ? actualEntry : entryPrice, qty,
                entryReason, plannedStop, stopReason, plannedExit, exitReason, exitPrice, exitDate, exitType, lesson);
    }

    /** Records the result of an executed trade. A blank lesson keeps the existing one. */
    public Trade withResult(BigDecimal price, LocalDate date, ExitType type, String newLesson) {
        return new Trade(id, symbol, companyName, tradeDate, entryPrice, quantity, entryReason,
                plannedStop, stopReason, plannedExit, exitReason, price, date, type,
                newLesson != null ? newLesson : lesson);
    }

    /** Fills in the exit type when the trade is closed but no type was given. */
    public Trade withDefaultExitType() {
        if (exitPrice == null || exitType != null) {
            return this;
        }
        ExitType type = exitPrice.compareTo(plannedExit) >= 0 ? ExitType.TARGET
                : exitPrice.compareTo(plannedStop) <= 0 ? ExitType.STOP : ExitType.MANUAL;
        return new Trade(id, symbol, companyName, tradeDate, entryPrice, quantity, entryReason,
                plannedStop, stopReason, plannedExit, exitReason, exitPrice, exitDate, type, lesson);
    }
}
