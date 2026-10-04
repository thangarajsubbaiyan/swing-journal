package com.swingjournal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Values calculated from a {@link Trade}; never typed in by hand. */
public record TradeMetrics(
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
        BigDecimal invested = t.entryPrice().multiply(t.quantity());
        BigDecimal riskPerShare = t.entryPrice().subtract(t.plannedStop());
        BigDecimal riskAmount = riskPerShare.multiply(t.quantity());

        BigDecimal riskPercent = null;
        BigDecimal rewardRisk = null;
        if (riskPerShare.signum() > 0) {
            riskPercent = riskPerShare.multiply(HUNDRED).divide(t.entryPrice(), 1, RoundingMode.HALF_UP);
            rewardRisk = t.plannedExit().subtract(t.entryPrice())
                    .divide(riskPerShare, 2, RoundingMode.HALF_UP);
        }

        BigDecimal pl = null;
        BigDecimal r = null;
        Outcome outcome = Outcome.OPEN;
        if (t.isClosed()) {
            BigDecimal rawPl = t.exitPrice().subtract(t.entryPrice()).multiply(t.quantity());
            pl = rawPl.setScale(2, RoundingMode.HALF_UP);
            if (riskAmount.signum() > 0) {
                r = rawPl.divide(riskAmount, 2, RoundingMode.HALF_UP);
            }
            outcome = pl.signum() > 0 ? Outcome.WIN : pl.signum() < 0 ? Outcome.LOSS : Outcome.BREAKEVEN;
        }
        return new TradeMetrics(invested.setScale(2, RoundingMode.HALF_UP), riskPerShare, 
                riskAmount.setScale(2, RoundingMode.HALF_UP), riskPercent, rewardRisk, pl, r, outcome);
    }
}
