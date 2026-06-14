package com.roots.mms.controller;

import com.roots.mms.entity.LocaleOption;
import com.roots.mms.service.LocaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LocaleController {

    private final LocaleService localeService;

    // ── Public endpoints (no auth) ─────────────────────────────────────

    @GetMapping("/api/locale/languages")
    public ResponseEntity<List<LocaleOption>> getLanguages() {
        return ResponseEntity.ok(localeService.getEnabledByType("LANGUAGE"));
    }

    @GetMapping("/api/locale/countries")
    public ResponseEntity<List<LocaleOption>> getCountries() {
        return ResponseEntity.ok(localeService.getEnabledByType("COUNTRY"));
    }

    @GetMapping("/api/locale/timezones")
    public ResponseEntity<List<LocaleOption>> getTimezones() {
        return ResponseEntity.ok(localeService.getEnabledByType("TIMEZONE"));
    }

    // ── Admin endpoints ────────────────────────────────────────────────

    @GetMapping("/api/admin/locale/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LocaleOption>> getAllByType(@PathVariable String type) {
        return ResponseEntity.ok(localeService.getAllByType(type.toUpperCase()));
    }

    @PostMapping("/api/admin/locale")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocaleOption> create(@RequestBody LocaleOption option) {
        return ResponseEntity.ok(localeService.create(option));
    }

    @PutMapping("/api/admin/locale/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocaleOption> update(@PathVariable String id,
                                               @RequestBody LocaleOption patch) {
        return ResponseEntity.ok(localeService.update(id, patch));
    }

    @DeleteMapping("/api/admin/locale/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        localeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
