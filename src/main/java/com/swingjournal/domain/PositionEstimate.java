package com.swingjournal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * "If I put this much capital into the trade, what is the most I can lose and make?"
 * Long trades only. Max loss assumes the stop fills exactly at the stop price (gaps and slippage can make the
 * real loss bigger) and ignores fees.
 */
public record PositionEstimate(
        BigDecimal quantity,
        BigDecimal amountInvested,
        BigDecimal cashLeft,
        BigDecimal maxLoss,
        BigDecimal maxLossPercentOfCapital,
        BigDecimal maxProfit,
        BigDecimal maxProfitPercentOfCapital,
        BigDecimal rewardRisk,
        BigDecimal stopDistancePercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param fractional true to allow fractional shares (4 decimals), false to round down to whole shares
     */
    public static PositionEstimate of(BigDecimal entry, BigDecimal stop, BigDecimal target,
                                      BigDecimal capital, boolean fractional) {
        require(entry != null && entry.signum() > 0, "entry price must be greater than 0");
        require(stop != null && stop.signum() > 0, "stop must be greater than 0");
        require(target != null && target.signum() > 0, "target must be greater than 0");
        require(capital != null && capital.signum() > 0, "capital must be greater than 0");
        require(stop.compareTo(entry) < 0, "stop must be below the entry price (long trades only)");
        require(target.compareTo(entry) > 0, "target must be above the entry price (long trades only)");

        BigDecimal quantity = capital.divide(entry, fractional ? 4 : 0, RoundingMode.DOWN);
        require(quantity.signum() > 0, "capital is less than the price of one share");

        BigDecimal riskPerShare = entry.subtract(stop);
        BigDecimal invested = entry.multiply(quantity);
        BigDecimal maxLoss = riskPerShare.multiply(quantity);
        BigDecimal maxProfit = target.subtract(entry).multiply(quantity);

        return new PositionEstimate(
                quantity.stripTrailingZeros().scale() < 0 ? quantity.setScale(0) : quantity.stripTrailingZeros(),
                scale2(invested),
                scale2(capital.subtract(invested)),
                scale2(maxLoss),
                scale2(maxLoss.multiply(HUNDRED).divide(capital, 6, RoundingMode.HALF_UP)),
                scale2(maxProfit),
                scale2(maxProfit.multiply(HUNDRED).divide(capital, 6, RoundingMode.HALF_UP)),
                target.subtract(entry).divide(riskPerShare, 2, RoundingMode.HALF_UP),
                scale2(riskPerShare.multiply(HUNDRED).divide(entry, 6, RoundingMode.HALF_UP)));
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
