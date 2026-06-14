package com.roots.mms.service;

import com.roots.mms.entity.LocaleOption;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.LocaleOptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocaleService {

    private final LocaleOptionRepository localeOptionRepository;

    // ── Startup seeding ────────────────────────────────────────────────

    @PostConstruct
    public void seedDefaults() {
        if (localeOptionRepository.count() > 0) {
            log.info("Locale options already seeded ({} entries), skipping.", localeOptionRepository.count());
            return;
        }
        log.info("Seeding default locale options...");
        seedLanguages();
        seedCountries();
        seedTimezones();
        log.info("Seeded {} locale options.", localeOptionRepository.count());
    }

    private void seedLanguages() {
        String[][] langs = {
            {"en", "English"}, {"es", "Spanish"}, {"fr", "French"}, {"de", "German"},
            {"pt", "Portuguese"}, {"it", "Italian"}, {"nl", "Dutch"}, {"ja", "Japanese"},
            {"zh", "Chinese (Simplified)"}, {"ko", "Korean"}, {"ar", "Arabic"},
            {"hi", "Hindi"}, {"ru", "Russian"}, {"tr", "Turkish"}, {"pl", "Polish"}
        };
        for (int i = 0; i < langs.length; i++) {
            localeOptionRepository.save(LocaleOption.builder()
                    .type("LANGUAGE").code(langs[i][0]).label(langs[i][1])
                    .sortOrder(i).enabled(true).build());
        }
    }

    private void seedCountries() {
        String[][] countries = {
            {"US", "United States"}, {"GB", "United Kingdom"}, {"CA", "Canada"},
            {"AU", "Australia"}, {"DE", "Germany"}, {"FR", "France"}, {"ES", "Spain"},
            {"IT", "Italy"}, {"BR", "Brazil"}, {"IN", "India"}, {"JP", "Japan"},
            {"CN", "China"}, {"KR", "South Korea"}, {"AE", "UAE"}, {"SG", "Singapore"},
            {"NL", "Netherlands"}, {"CH", "Switzerland"}, {"SE", "Sweden"},
            {"NO", "Norway"}, {"IE", "Ireland"}, {"IL", "Israel"}, {"SA", "Saudi Arabia"},
            {"MX", "Mexico"}, {"AR", "Argentina"}, {"CL", "Chile"}
        };
        for (int i = 0; i < countries.length; i++) {
            localeOptionRepository.save(LocaleOption.builder()
                    .type("COUNTRY").code(countries[i][0]).label(countries[i][1])
                    .sortOrder(i).enabled(true).build());
        }
    }

    private void seedTimezones() {
        String[][] timezones = {
            {"America/New_York", "Eastern (ET)"}, {"America/Chicago", "Central (CT)"},
            {"America/Denver", "Mountain (MT)"}, {"America/Los_Angeles", "Pacific (PT)"},
            {"America/Anchorage", "Alaska (AKT)"}, {"Pacific/Honolulu", "Hawaii (HT)"},
            {"Europe/London", "London (GMT)"}, {"Europe/Paris", "Paris (CET)"},
            {"Europe/Berlin", "Berlin (CET)"}, {"Asia/Dubai", "Dubai (GST)"},
            {"Asia/Kolkata", "India (IST)"}, {"Asia/Singapore", "Singapore (SGT)"},
            {"Asia/Tokyo", "Tokyo (JST)"}, {"Asia/Shanghai", "Shanghai (CST)"},
            {"Australia/Sydney", "Sydney (AEST)"}, {"Pacific/Auckland", "Auckland (NZST)"},
            {"UTC", "UTC"}
        };
        for (int i = 0; i < timezones.length; i++) {
            localeOptionRepository.save(LocaleOption.builder()
                    .type("TIMEZONE").code(timezones[i][0]).label(timezones[i][1])
                    .sortOrder(i).enabled(true).build());
        }
    }

    // ── Public queries ─────────────────────────────────────────────────

    public List<LocaleOption> getEnabledByType(String type) {
        return localeOptionRepository.findByTypeAndEnabledTrueOrderBySortOrderAsc(type);
    }

    // ── Admin CRUD ─────────────────────────────────────────────────────

    public List<LocaleOption> getAllByType(String type) {
        return localeOptionRepository.findByTypeOrderBySortOrderAsc(type);
    }

    public LocaleOption create(LocaleOption option) {
        if (localeOptionRepository.existsByTypeAndCode(option.getType(), option.getCode())) {
            throw new DuplicateResourceException("LocaleOption", "type+code",
                    option.getType() + "/" + option.getCode());
        }
        return localeOptionRepository.save(option);
    }

    public LocaleOption update(String id, LocaleOption patch) {
        LocaleOption existing = localeOptionRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("LocaleOption", "id", id));
        if (patch.getLabel() != null) existing.setLabel(patch.getLabel());
        if (patch.getCode() != null) existing.setCode(patch.getCode());
        if (patch.getType() != null) existing.setType(patch.getType());
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());
        return localeOptionRepository.save(existing);
    }

    public void delete(String id) {
        LocaleOption option = localeOptionRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("LocaleOption", "id", id));
        localeOptionRepository.delete(option);
    }
}
