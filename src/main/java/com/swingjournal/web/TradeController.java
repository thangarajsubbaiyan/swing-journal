package com.swingjournal.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.swingjournal.csv.TradeCsvImporter;
import com.swingjournal.domain.ExitType;
import com.swingjournal.domain.Outcome;
import com.swingjournal.domain.Status;
import com.swingjournal.domain.Trade;
import com.swingjournal.domain.TradeMetrics;
import com.swingjournal.domain.TradeValidator;
import com.swingjournal.store.TradeRepository;

@RestController
@RequestMapping("/api")
public class TradeController {

    public record TradeView(Trade trade, TradeMetrics metrics) {
    }

    /** All editable fields. Used to create a trade and to edit it in any phase. */
    public record TradeRequest(String symbol, String companyName, LocalDate tradeDate, BigDecimal entryPrice,
                               BigDecimal quantity, String entryReason, BigDecimal plannedStop, String stopReason,
                               BigDecimal plannedExit, String exitReason, BigDecimal exitPrice, LocalDate exitDate,
                               ExitType exitType, String lesson) {
    }

    /** Phase 2: the plan was acted on. entryPrice is the actual fill; omit it to keep the planned one. */
    public record ExecuteTrade(LocalDate tradeDate, BigDecimal entryPrice, BigDecimal quantity) {
    }

    /** Phase 3: the trade is finished. exitType is worked out from the prices if omitted. */
    public record CloseTrade(BigDecimal exitPrice, LocalDate exitDate, ExitType exitType, String lesson) {
    }

    public record ImportResult(int imported, int skippedDuplicates, List<String> errors) {
    }

    public record Stats(int totalTrades, int plannedTrades, int openTrades, int closedTrades, int wins, int losses,
                        BigDecimal winRatePercent, BigDecimal totalProfitLoss, BigDecimal averageR) {
    }

    private final TradeRepository repo;

    public TradeController(TradeRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/trades")
    public List<TradeView> list() {
        return repo.findAll().stream().map(TradeController::view).toList();
    }

    @PostMapping("/trades")
    public ResponseEntity<TradeView> create(@RequestBody TradeRequest r) {
        Trade trade = prepare(toTrade(null, r));
        return repo.insert(trade)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(view(saved)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    @PutMapping("/trades/{id}")
    public ResponseEntity<TradeView> edit(@PathVariable long id, @RequestBody TradeRequest r) {
        return change(id, existing -> toTrade(id, r));
    }

    @PostMapping("/trades/{id}/execute")
    public ResponseEntity<TradeView> execute(@PathVariable long id, @RequestBody ExecuteTrade e) {
        if (e.tradeDate() == null) {
            throw new IllegalArgumentException("trade date is required to execute a trade");
        }
        return change(id, t -> t.withExecution(e.tradeDate(), e.entryPrice(), e.quantity()));
    }

    @PostMapping("/trades/{id}/close")
    public ResponseEntity<TradeView> close(@PathVariable long id, @RequestBody CloseTrade c) {
        if (c.exitPrice() == null) {
            throw new IllegalArgumentException("exit price is required to close a trade");
        }
        return change(id, t -> t.withResult(c.exitPrice(), c.exitDate(), c.exitType(), blankToNull(c.lesson())));
    }

    @DeleteMapping("/trades/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return repo.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/import")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        TradeCsvImporter.ParseResult parsed =
                TradeCsvImporter.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        List<String> errors = new ArrayList<>(parsed.errors());
        int imported = 0;
        int skipped = 0;
        for (Trade t : parsed.trades()) {
            try {
                Trade ready = prepare(t);
                if (repo.insert(ready).isPresent()) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (IllegalArgumentException e) {
                errors.add(t.symbol() + ": " + e.getMessage());
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    @GetMapping("/stats")
    public Stats stats() {
        List<Trade> all = repo.findAll();
        int planned = 0;
        int open = 0;
        int closed = 0;
        int wins = 0;
        int losses = 0;
        BigDecimal totalPl = BigDecimal.ZERO;
        BigDecimal rSum = BigDecimal.ZERO;
        int rCount = 0;
        for (Trade t : all) {
            TradeMetrics m = TradeMetrics.of(t);
            if (m.status() == Status.PLANNED) {
                planned++;
            } else if (m.status() == Status.EXECUTED) {
                open++;
            } else {
                closed++;
                if (m.profitLoss() != null) {
                    totalPl = totalPl.add(m.profitLoss());
                }
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
        }
        BigDecimal winRate = closed == 0 ? null
                : BigDecimal.valueOf(wins * 100L).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP);
        BigDecimal avgR = rCount == 0 ? null : rSum.divide(BigDecimal.valueOf(rCount), 2, RoundingMode.HALF_UP);
        return new Stats(all.size(), planned, open, closed, wins, losses, winRate, totalPl, avgR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Loads a trade, applies a change, validates the result and saves it. */
    private ResponseEntity<TradeView> change(long id, UnaryOperator<Trade> change) {
        Trade existing = repo.find(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Trade updated = prepare(change.apply(existing));
        try {
            repo.update(id, updated);
        } catch (DataAccessException e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("UNIQUE")) {
                throw new IllegalArgumentException(
                        "Another trade with the same symbol, date and entry price already exists");
            }
            throw e;
        }
        return ResponseEntity.ok(view(repo.find(id).orElseThrow()));
    }

    private static Trade prepare(Trade t) {
        Trade ready = t.withDefaultExitType();
        TradeValidator.validate(ready);
        return ready;
    }

    private static Trade toTrade(Long id, TradeRequest r) {
        return new Trade(id,
                r.symbol() == null ? null : r.symbol().trim().toUpperCase(),
                blankToNull(r.companyName()), r.tradeDate(), r.entryPrice(), r.quantity(),
                blankToNull(r.entryReason()), r.plannedStop(), blankToNull(r.stopReason()),
                r.plannedExit(), blankToNull(r.exitReason()), r.exitPrice(), r.exitDate(), r.exitType(),
                blankToNull(r.lesson()));
    }

    private static TradeView view(Trade t) {
        return new TradeView(t, TradeMetrics.of(t));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
