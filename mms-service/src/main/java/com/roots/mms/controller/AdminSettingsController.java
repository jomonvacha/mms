package com.roots.mms.controller;

import com.roots.mms.entity.AppSetting;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Slf4j
public class AdminSettingsController {

    private final AppSettingService settingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppSetting>> list() {
        return ResponseEntity.ok(settingService.listAll());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppSetting> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) throw new BusinessRuleException("value is required");
        return ResponseEntity.ok(settingService.set(key, value, SecurityUtils.getCurrentUserIdOrNull()));
    }
}
