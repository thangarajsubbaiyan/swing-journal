package com.swingjournal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.swingjournal.csv.TradeCsvImporter;
import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Outcome;
import com.swingjournal.domain.Trade;
import com.swingjournal.domain.TradeMetrics;

class TradeLogicTest {

    private static final String HEADER = "Symbol,Trade date,Entry price,Entry price Strategy,Quantity,Amount invested,"
            + "Planned Stop loss,Stop loss Strategy,Risk/Reward ratio,Executed Stop loss price,Stop loss executed date,"
            + "Planned Exit price,Exit price Strategy,Executed exit price,Profit/Loss,Exit date,Success/Failure,Strategy,Lesson learned\n";

    @Test
    void openTradeCalculatesPlanMetrics() {
        Trade t = new Trade(null, "TT", null, LocalDate.of(2024, 6, 27), new BigDecimal("331.85"), BigDecimal.ONE,
                null, new BigDecimal("318.75"), null, new BigDecimal("358.86"), null, null, null, null, null);
        TradeMetrics m = TradeMetrics.of(t);
        assertEquals(new BigDecimal("331.85"), m.amountInvested());
        assertEquals(new BigDecimal("2.06"), m.rewardRisk());
        assertEquals(new BigDecimal("3.9"), m.riskPercent());
        assertEquals(Outcome.OPEN, m.outcome());
        assertNull(m.profitLoss());
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
        TradeMetrics m = TradeMetrics.of(t);
        assertEquals(Outcome.LOSS, m.outcome());
        assertEquals(new BigDecimal("-3.29"), m.profitLoss());
        assertEquals(new BigDecimal("-1.00"), m.rMultiple());
    }

    @Test
    void reportsMissingColumnsAndBadRows() {
        assertTrue(TradeCsvImporter.parse("Symbol\nA").errors().get(0).startsWith("Missing required column"));
        var bad = TradeCsvImporter.parse("Symbol,Trade date,Entry price,Quantity,Planned Stop loss,Planned Exit price\n"
                + "BRK-B,13/45/2024,10,1,9,12\nBRK-B,1/2/2024,10,1,9,12\n");
        assertEquals(1, bad.errors().size());
        assertEquals("BRK-B", bad.trades().get(0).symbol());
    }
}
