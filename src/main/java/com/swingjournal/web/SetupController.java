package com.swingjournal.web;

import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;

import com.swingjournal.domain.Setup;
import com.swingjournal.store.SetupRepository;

@RestController
@RequestMapping("/api/setups")
public class SetupController {

    public record SetupRequest(String name, String description, String tradingNotes, String checklist,
                               String warnings) {
    }

    private final SetupRepository repo;

    public SetupController(SetupRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Setup> list() {
        return repo.findAll();
    }

    @PostMapping
    public ResponseEntity<Setup> create(@RequestBody SetupRequest r) {
        Setup setup = toSetup(r);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(repo.insert(setup));
        } catch (DataAccessException e) {
            throw duplicateOrRethrow(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Setup> edit(@PathVariable long id, @RequestBody SetupRequest r) {
        Setup setup = toSetup(r);
        try {
            if (!repo.update(id, setup)) {
                return ResponseEntity.notFound().build();
            }
        } catch (DataAccessException e) {
            throw duplicateOrRethrow(e);
        }
        return ResponseEntity.ok(repo.find(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return repo.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private static Setup toSetup(SetupRequest r) {
        if (r.name() == null || r.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return new Setup(null, r.name().trim(), clean(r.description()), clean(r.tradingNotes()),
                clean(r.checklist()), clean(r.warnings()));
    }

    private static RuntimeException duplicateOrRethrow(DataAccessException e) {
        if (String.valueOf(e.getMessage()).contains("UNIQUE")) {
            return new IllegalArgumentException("A setup with this name already exists");
        }
        return e;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
