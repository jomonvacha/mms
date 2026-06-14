package com.roots.mms.controller;

import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member-facing read surface for the AI model catalog. Used by idfy-service
 * (phase 3 onwards) to resolve which models a given member can select.
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelsController {

    private final AiModelService service;

    /**
     * Returns the caller's resolved catalog — included + locked models merged
     * and sorted, with the tier default highlighted.
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiModelService.ResolvedCatalog> mine() {
        String uid = SecurityUtils.getCurrentUserIdOrNull();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.resolveForMember(uid));
    }
}
