package com.swingjournal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.swingjournal.csv.TradeCsvImporter;
import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Outcome;
import com.swingjournal.domain.PositionEstimate;
import com.swingjournal.domain.Status;
import com.swingjournal.domain.Trade;
import com.swingjournal.domain.TradeMetrics;
import com.swingjournal.domain.TradeValidator;

class TradeLogicTest {

    private static final String HEADER = "Symbol,Trade date,Entry price,Entry price Strategy,Quantity,Amount invested,"
            + "Planned Stop loss,Stop loss Strategy,Risk/Reward ratio,Executed Stop loss price,Stop loss executed date,"
            + "Planned Exit price,Exit price Strategy,Executed exit price,Profit/Loss,Exit date,Success/Failure,Strategy,Lesson learned\n";

    private static Trade planned() {
        return new Trade(null, "TT", null, null, new BigDecimal("331.85"), null, null,
                new BigDecimal("318.75"), null, new BigDecimal("358.86"), null, null, null, null, null, null, null);
    }

    @Test
    void plannedTradeNeedsNoDateOrQuantity() {
        Trade t = planned();
        TradeValidator.validate(t);
        TradeMetrics m = TradeMetrics.of(t);
        assertEquals(Status.PLANNED, m.status());
        assertEquals(new BigDecimal("2.06"), m.rewardRisk());
        assertEquals(new BigDecimal("3.9"), m.riskPercent());
        assertNull(m.amountInvested());
        assertEquals(Outcome.PENDING, m.outcome());
    }

    @Test
    void planThenExecuteThenClose() {
        Trade executed = planned().withExecution(LocalDate.of(2024, 6, 27), new BigDecimal("332.00"), BigDecimal.TEN);
        TradeValidator.validate(executed);
        assertEquals(Status.EXECUTED, TradeMetrics.of(executed).status());
        assertEquals(new BigDecimal("3320.00"), TradeMetrics.of(executed).amountInvested());

        Trade closed = executed.withResult(new BigDecimal("358.86"), LocalDate.of(2024, 7, 10), null, "Held to target")
                .withDefaultExitType();
        TradeValidator.validate(closed);
        TradeMetrics m = TradeMetrics.of(closed);
        assertEquals(Status.CLOSED, m.status());
        assertEquals(Outcome.WIN, m.outcome());
        assertEquals(ExitType.TARGET, closed.exitType());
        assertEquals(new BigDecimal("268.60"), m.profitLoss());
        assertEquals("Held to target", closed.lesson());
    }

    @Test
    void validatorEnforcesPhaseRules() {
        Trade datedWithoutQty = planned().withExecution(LocalDate.of(2024, 6, 27), null, null);
        assertThrows(IllegalArgumentException.class, () -> TradeValidator.validate(datedWithoutQty));

        Trade resultWithoutExecution = planned().withResult(new BigDecimal("340"), LocalDate.of(2024, 7, 1), null, null);
        assertThrows(IllegalArgumentException.class, () -> TradeValidator.validate(resultWithoutExecution));

        Trade executed = planned().withExecution(LocalDate.of(2024, 6, 27), null, BigDecimal.ONE);
        Trade exitBeforeEntry = executed.withResult(new BigDecimal("340"), LocalDate.of(2024, 6, 1), null, null);
        assertThrows(IllegalArgumentException.class, () -> TradeValidator.validate(exitBeforeEntry));
    }

    @Test
    void importsStoppedOutTradeFromSpreadsheetCsv() {
        String csv = HEADER
                + "CMG - Chipotle Mexican grill,06/25/2024,64.93,\"Channel up\nbounce\",1.0369,67.33,61.77,\"Below support\","
                + "2.02,61.76,07/02/2024,71.35,Trend line,,-3.3,,Failure,dup,\"Avoid events\"\n";
        var result = TradeCsvImporter.parse(csv);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        Trade t = result.trades().get(0);
        assertEquals("CMG", t.symbol());
        assertEquals("Chipotle Mexican grill", t.companyName());
        assertEquals(LocalDate.of(2024, 6, 25), t.tradeDate());
        assertEquals(ExitType.STOP, t.exitType());
        assertEquals(LocalDate.of(2024, 7, 2), t.exitDate());
        TradeValidator.validate(t);
        TradeMetrics m = TradeMetrics.of(t);
        assertEquals(Outcome.LOSS, m.outcome());
        assertEquals(new BigDecimal("-3.29"), m.profitLoss());
        assertEquals(new BigDecimal("-1.00"), m.rMultiple());
    }

    @Test
    void csvRowWithoutDateOrQuantityImportsAsPlanned() {
        var result = TradeCsvImporter.parse("Symbol,Entry price,Planned Stop loss,Planned Exit price\nSAP,205.82,195,236\n");
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(Status.PLANNED, TradeMetrics.of(result.trades().get(0)).status());
    }

    @Test
    void reportsMissingColumnsAndBadRows() {
        assertTrue(TradeCsvImporter.parse("Symbol\nA").errors().get(0).startsWith("Missing required column"));
        var bad = TradeCsvImporter.parse("Symbol,Trade date,Entry price,Quantity,Planned Stop loss,Planned Exit price\n"
                + "BRK-B,13/45/2024,10,1,9,12\nBRK-B,1/2/2024,10,1,9,12\n");
        assertEquals(1, bad.errors().size());
        assertEquals("BRK-B", bad.trades().get(0).symbol());
    }
    @Test
    void estimatesMaxLossAndProfitForGivenCapital() {
        PositionEstimate e = PositionEstimate.of(new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("115"),
                new BigDecimal("10000"), false);
        assertEquals(new BigDecimal("100"), e.quantity());
        assertEquals(new BigDecimal("500.00"), e.maxLoss());
        assertEquals(new BigDecimal("5.00"), e.maxLossPercentOfCapital());
        assertEquals(new BigDecimal("1500.00"), e.maxProfit());
        assertEquals(new BigDecimal("3.00"), e.rewardRisk());
        assertEquals(new BigDecimal("0.00"), e.cashLeft());
    }

    @Test
    void estimateRoundsDownToWholeSharesUnlessFractional() {
        BigDecimal entry = new BigDecimal("331.85");
        BigDecimal stop = new BigDecimal("318.75");
        BigDecimal target = new BigDecimal("358.86");
        PositionEstimate whole = PositionEstimate.of(entry, stop, target, new BigDecimal("1000"), false);
        assertEquals(new BigDecimal("3"), whole.quantity());
        assertEquals(new BigDecimal("995.55"), whole.amountInvested());
        assertEquals(new BigDecimal("4.45"), whole.cashLeft());
        assertEquals(new BigDecimal("39.30"), whole.maxLoss());
        assertEquals(new BigDecimal("81.03"), whole.maxProfit());
        assertEquals(new BigDecimal("2.06"), whole.rewardRisk());

        PositionEstimate frac = PositionEstimate.of(entry, stop, target, new BigDecimal("1000"), true);
        assertEquals(new BigDecimal("3.0134"), frac.quantity());
    }

    @Test
    void estimateRejectsBadInputs() {
        BigDecimal ten = BigDecimal.TEN;
        assertThrows(IllegalArgumentException.class, () -> PositionEstimate.of(ten, new BigDecimal("11"), new BigDecimal("12"), ten, true));
        assertThrows(IllegalArgumentException.class, () -> PositionEstimate.of(ten, new BigDecimal("9"), new BigDecimal("9.5"), ten, true));
        assertThrows(IllegalArgumentException.class, () -> PositionEstimate.of(ten, new BigDecimal("9"), new BigDecimal("12"), BigDecimal.ONE, false));
        assertThrows(IllegalArgumentException.class, () -> PositionEstimate.of(ten, new BigDecimal("9"), new BigDecimal("12"), BigDecimal.ZERO, false));
    }
}
