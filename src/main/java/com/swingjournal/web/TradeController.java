package com.swingjournal.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.swingjournal.csv.TradeCsvImporter;
import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Outcome;
import com.swingjournal.domain.Trade;
import com.swingjournal.domain.TradeMetrics;
import com.swingjournal.store.TradeRepository;

@RestController
@RequestMapping("/api")
public class TradeController {

    public record TradeView(Trade trade, TradeMetrics metrics) {
    }

    public record NewTrade(String symbol, String companyName, LocalDate tradeDate, BigDecimal entryPrice,
                           BigDecimal quantity, String entryReason, BigDecimal plannedStop, String stopReason,
                           BigDecimal plannedExit, String exitReason) {
    }

    public record CloseTrade(BigDecimal exitPrice, LocalDate exitDate, ExitType exitType, String lesson) {
    }

    public record ImportResult(int imported, int skippedDuplicates, List<String> errors) {
    }

    public record Stats(int totalTrades, int openTrades, int closedTrades, int wins, int losses,
                        BigDecimal winRatePercent, BigDecimal totalProfitLoss, BigDecimal averageR) {
    }

    private final TradeRepository repo;

    public TradeController(TradeRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/trades")
    public List<TradeView> list() {
        return repo.findAll().stream().map(t -> new TradeView(t, TradeMetrics.of(t))).toList();
    }

    @PostMapping("/trades")
    public ResponseEntity<TradeView> create(@RequestBody NewTrade n) {
        require(n.symbol() != null && !n.symbol().isBlank(), "symbol is required");
        require(n.tradeDate() != null, "tradeDate is required");
        require(positive(n.entryPrice()), "entryPrice must be greater than 0");
        require(positive(n.quantity()), "quantity must be greater than 0");
        require(positive(n.plannedStop()), "plannedStop must be greater than 0");
        require(positive(n.plannedExit()), "plannedExit must be greater than 0");
        require(n.plannedStop().compareTo(n.entryPrice()) < 0, "plannedStop must be below entryPrice (long trades only)");
        require(n.plannedExit().compareTo(n.entryPrice()) > 0, "plannedExit must be above entryPrice (long trades only)");

        Trade trade = new Trade(null, n.symbol().trim().toUpperCase(), n.companyName(), n.tradeDate(),
                n.entryPrice(), n.quantity(), n.entryReason(), n.plannedStop(), n.stopReason(),
                n.plannedExit(), n.exitReason(), null, null, null, null);
        return repo.insert(trade)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(new TradeView(saved, TradeMetrics.of(saved))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    @PostMapping("/trades/{id}/close")
    public ResponseEntity<TradeView> close(@PathVariable long id, @RequestBody CloseTrade c) {
        require(positive(c.exitPrice()), "exitPrice must be greater than 0");
        require(c.exitDate() != null, "exitDate is required");
        Trade existing = repo.find(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        require(!c.exitDate().isBefore(existing.tradeDate()), "exitDate can't be before the trade date");
        ExitType type = c.exitType() != null ? c.exitType()
                : c.exitPrice().compareTo(existing.plannedExit()) >= 0 ? ExitType.TARGET
                : c.exitPrice().compareTo(existing.plannedStop()) <= 0 ? ExitType.STOP : ExitType.MANUAL;
        repo.close(id, c.exitPrice(), c.exitDate(), type, blankToNull(c.lesson()));
        Trade updated = repo.find(id).orElseThrow();
        return ResponseEntity.ok(new TradeView(updated, TradeMetrics.of(updated)));
    }

    @PostMapping("/import")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        TradeCsvImporter.ParseResult parsed =
                TradeCsvImporter.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        int imported = 0;
        int skipped = 0;
        for (Trade t : parsed.trades()) {
            if (repo.insert(t).isPresent()) {
                imported++;
            } else {
                skipped++;
            }
        }
        return new ImportResult(imported, skipped, new ArrayList<>(parsed.errors()));
    }

    @GetMapping("/stats")
    public Stats stats() {
        List<Trade> all = repo.findAll();
        int wins = 0;
        int losses = 0;
        int closed = 0;
        BigDecimal totalPl = BigDecimal.ZERO;
        BigDecimal rSum = BigDecimal.ZERO;
        int rCount = 0;
        for (Trade t : all) {
            if (!t.isClosed()) {
                continue;
            }
            closed++;
            TradeMetrics m = TradeMetrics.of(t);
            totalPl = totalPl.add(m.profitLoss());
            if (m.outcome() == Outcome.WIN) {
                wins++;
            } else if (m.outcome() == Outcome.LOSS) {
                losses++;
            }
            if (m.rMultiple() != null) {
                rSum = rSum.add(m.rMultiple());
                rCount++;
            }
        }
        BigDecimal winRate = closed == 0 ? null
                : BigDecimal.valueOf(wins * 100L).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP);
        BigDecimal avgR = rCount == 0 ? null : rSum.divide(BigDecimal.valueOf(rCount), 2, RoundingMode.HALF_UP);
        return new Stats(all.size(), all.size() - closed, closed, wins, losses, winRate, totalPl, avgR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
