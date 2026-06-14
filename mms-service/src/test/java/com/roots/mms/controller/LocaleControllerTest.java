package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.LocaleOption;
import com.roots.mms.service.LocaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocaleControllerTest {

    private LocaleService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(LocaleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LocaleController(service)).build();
    }

    private LocaleOption opt(String type, String code, String label) {
        LocaleOption o = new LocaleOption();
        o.setType(type);
        o.setCode(code);
        o.setLabel(label);
        return o;
    }

    @Test
    void getLanguages_delegatesToServiceWithLanguageType() throws Exception {
        when(service.getEnabledByType("LANGUAGE")).thenReturn(List.of(opt("LANGUAGE", "en", "English")));

        mockMvc.perform(get("/api/locale/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("en"));

        verify(service).getEnabledByType("LANGUAGE");
    }

    @Test
    void getCountries_delegatesWithCountryType() throws Exception {
        when(service.getEnabledByType("COUNTRY")).thenReturn(List.of());

        mockMvc.perform(get("/api/locale/countries"))
                .andExpect(status().isOk());

        verify(service).getEnabledByType("COUNTRY");
    }

    @Test
    void getTimezones_delegatesWithTimezoneType() throws Exception {
        when(service.getEnabledByType("TIMEZONE")).thenReturn(List.of());

        mockMvc.perform(get("/api/locale/timezones"))
                .andExpect(status().isOk());

        verify(service).getEnabledByType("TIMEZONE");
    }

    @Test
    void getAllByType_uppercasesPathVariable() throws Exception {
        // Admin endpoint accepts whatever case the caller used; the controller
        // normalizes to uppercase before hitting the service. Documents the
        // contract so future callers don't need to remember to uppercase.
        when(service.getAllByType("LANGUAGE")).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/locale/language"))
                .andExpect(status().isOk());

        verify(service).getAllByType("LANGUAGE");
    }

    @Test
    void create_returnsCreatedOption() throws Exception {
        LocaleOption created = opt("LANGUAGE", "fr", "French");
        when(service.create(any(LocaleOption.class))).thenReturn(created);

        String body = objectMapper.writeValueAsString(opt("LANGUAGE", "fr", "French"));
        mockMvc.perform(post("/api/admin/locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("fr"));
    }

    @Test
    void update_passesIdAndPatchToService() throws Exception {
        LocaleOption updated = opt("LANGUAGE", "fr", "Français");
        when(service.update(any(), any(LocaleOption.class))).thenReturn(updated);

        String body = objectMapper.writeValueAsString(opt("LANGUAGE", "fr", "Français"));
        mockMvc.perform(put("/api/admin/locale/abc-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Français"));

        verify(service).update(eq("abc-id"), any(LocaleOption.class));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/admin/locale/abc-id"))
                .andExpect(status().isNoContent());

        verify(service).delete("abc-id");
    }
}
