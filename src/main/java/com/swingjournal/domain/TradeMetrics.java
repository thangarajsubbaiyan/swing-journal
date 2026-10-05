package com.swingjournal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Values calculated from a {@link Trade}; never typed in by hand.
 * Anything that needs a quantity is null until the trade has one.
 */
public record TradeMetrics(
        Status status,
        BigDecimal amountInvested,
        BigDecimal riskPerShare,
        BigDecimal riskAmount,
        BigDecimal riskPercent,
        BigDecimal rewardRisk,
        BigDecimal profitLoss,
        BigDecimal rMultiple,
        Outcome outcome) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static TradeMetrics of(Trade t) {
        BigDecimal qty = t.quantity();
        BigDecimal riskPerShare = t.entryPrice().subtract(t.plannedStop());

        BigDecimal invested = qty == null ? null : scale2(t.entryPrice().multiply(qty));
        BigDecimal riskAmount = qty == null ? null : scale2(riskPerShare.multiply(qty));

        BigDecimal riskPercent = null;
        BigDecimal rewardRisk = null;
        if (riskPerShare.signum() > 0) {
            riskPercent = riskPerShare.multiply(HUNDRED).divide(t.entryPrice(), 1, RoundingMode.HALF_UP);
            rewardRisk = t.plannedExit().subtract(t.entryPrice())
                    .divide(riskPerShare, 2, RoundingMode.HALF_UP);
        }

        Status status = t.isClosed() ? Status.CLOSED : t.isExecuted() ? Status.EXECUTED : Status.PLANNED;
        BigDecimal pl = null;
        BigDecimal r = null;
        Outcome outcome = Outcome.PENDING;
        if (t.isClosed()) {
            BigDecimal movePerShare = t.exitPrice().subtract(t.entryPrice());
            if (qty != null) {
                pl = scale2(movePerShare.multiply(qty));
            }
            if (riskPerShare.signum() > 0) {
                r = movePerShare.divide(riskPerShare, 2, RoundingMode.HALF_UP);
            }
            outcome = movePerShare.signum() > 0 ? Outcome.WIN
                    : movePerShare.signum() < 0 ? Outcome.LOSS : Outcome.BREAKEVEN;
        }
        return new TradeMetrics(status, invested, riskPerShare, riskAmount, riskPercent, rewardRisk, pl, r, outcome);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
