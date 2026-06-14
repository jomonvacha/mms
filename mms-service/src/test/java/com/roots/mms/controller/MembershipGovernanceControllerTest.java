package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.dto.request.UpsertCategoryRequest;
import com.roots.mms.dto.request.UpsertTierEntitlementRequest;
import com.roots.mms.dto.request.UpsertTierRequest;
import com.roots.mms.dto.response.CategoryResponse;
import com.roots.mms.dto.response.EntitlementResponse;
import com.roots.mms.dto.response.TierResponse;
import com.roots.mms.entity.Entitlement;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.service.EntitlementService;
import com.roots.mms.service.MembershipGovernanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

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

class MembershipGovernanceControllerTest {

    private MembershipGovernanceService governanceService;
    private EntitlementService entitlementService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        governanceService = mock(MembershipGovernanceService.class);
        entitlementService = mock(EntitlementService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MembershipGovernanceController(governanceService, entitlementService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Categories ─────────────────────────────────────────────────────

    @Test
    void listCategories_returnsList() throws Exception {
        when(governanceService.listCategoriesWithTiers()).thenReturn(List.of(
                CategoryResponse.builder().code("PERSONAL").displayName("Personal")
                        .tiers(List.of()).build()));

        mockMvc.perform(get("/api/governance/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PERSONAL"));
    }

    @Test
    void getCategory_byCode_returnsCategory() throws Exception {
        when(governanceService.getCategory("PERSONAL")).thenReturn(
                CategoryResponse.builder().code("PERSONAL").tiers(List.of()).build());

        mockMvc.perform(get("/api/governance/categories/PERSONAL"))
                .andExpect(status().isOk());
    }

    @Test
    void createCategory_invalidCodePattern_returns400() throws Exception {
        // Code regex: ^[A-Z][A-Z0-9_]{1,31}$ — lowercase fails @Valid.
        UpsertCategoryRequest req = UpsertCategoryRequest.builder().code("personal").build();

        mockMvc.perform(post("/api/governance/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategory_valid_delegatesToService() throws Exception {
        UpsertCategoryRequest req = UpsertCategoryRequest.builder()
                .code("CUSTOM").displayName("Custom").build();
        when(governanceService.createCategory(any(UpsertCategoryRequest.class)))
                .thenReturn(CategoryResponse.builder().code("CUSTOM").tiers(List.of()).build());

        mockMvc.perform(post("/api/governance/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CUSTOM"));
    }

    @Test
    void updateCategory_byCode_delegates() throws Exception {
        when(governanceService.updateCategory(eq("PERSONAL"), any(UpsertCategoryRequest.class)))
                .thenReturn(CategoryResponse.builder().code("PERSONAL").enabled(false)
                        .tiers(List.of()).build());

        UpsertCategoryRequest req = UpsertCategoryRequest.builder().enabled(false).build();

        mockMvc.perform(put("/api/governance/categories/PERSONAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void deleteCategory_returnsMessageResponse() throws Exception {
        mockMvc.perform(delete("/api/governance/categories/CUSTOM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Category deleted"));

        verify(governanceService).deleteCategory("CUSTOM");
    }

    // ── Tiers ──────────────────────────────────────────────────────────

    @Test
    void listTiers_byCategory_returnsList() throws Exception {
        when(governanceService.listTiers("PERSONAL")).thenReturn(List.of(
                TierResponse.builder().categoryCode("PERSONAL").tierCode("FREE").build()));

        mockMvc.perform(get("/api/governance/categories/PERSONAL/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tierCode").value("FREE"));
    }

    @Test
    void listAllTiers_returnsList() throws Exception {
        when(governanceService.listAllTiers()).thenReturn(List.of());

        mockMvc.perform(get("/api/governance/tiers"))
                .andExpect(status().isOk());
    }

    @Test
    void createTier_invalidTierCodePattern_returns400() throws Exception {
        UpsertTierRequest req = UpsertTierRequest.builder().tierCode("free").build();

        mockMvc.perform(post("/api/governance/categories/PERSONAL/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTier_valid_delegates() throws Exception {
        UpsertTierRequest req = UpsertTierRequest.builder().tierCode("PRO").build();
        when(governanceService.createTier(eq("PERSONAL"), any(UpsertTierRequest.class)))
                .thenReturn(TierResponse.builder().categoryCode("PERSONAL").tierCode("PRO").build());

        mockMvc.perform(post("/api/governance/categories/PERSONAL/tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateTier_passesBothPathVarsAndPatch() throws Exception {
        UpsertTierRequest req = UpsertTierRequest.builder().displayName("Free Tier").build();
        when(governanceService.updateTier(eq("PERSONAL"), eq("FREE"), any(UpsertTierRequest.class)))
                .thenReturn(TierResponse.builder().tierCode("FREE").displayName("Free Tier").build());

        mockMvc.perform(put("/api/governance/categories/PERSONAL/tiers/FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTier_returnsMessageResponse() throws Exception {
        mockMvc.perform(delete("/api/governance/categories/PERSONAL/tiers/CUSTOM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tier deleted"));

        verify(governanceService).deleteTier("PERSONAL", "CUSTOM");
    }

    // ── Entitlement definitions ────────────────────────────────────────

    @Test
    void listEntitlements_returnsList() throws Exception {
        when(entitlementService.listEntitlements()).thenReturn(List.of(
                EntitlementResponse.builder().key("a").build()));

        mockMvc.perform(get("/api/governance/entitlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("a"));
    }

    @Test
    void createEntitlement_passesPayload() throws Exception {
        Entitlement e = Entitlement.builder().key("k").displayName("K")
                .valueType(Entitlement.ValueType.BOOLEAN).defaultValue("false").build();
        when(entitlementService.createEntitlement(any(Entitlement.class)))
                .thenReturn(EntitlementResponse.builder().key("k").build());

        mockMvc.perform(post("/api/governance/entitlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(e)))
                .andExpect(status().isOk());
    }

    @Test
    void updateEntitlement_overridesPayloadKeyWithPathVar() throws Exception {
        // The controller forces payload.key to the path variable so that a
        // mistyped body can't sneak past the URL contract. This guards
        // against a "rename via PUT" exploit.
        Entitlement payload = Entitlement.builder().key("WRONG").displayName("X")
                .valueType(Entitlement.ValueType.BOOLEAN).defaultValue("false").build();
        when(entitlementService.upsertEntitlement(any(Entitlement.class)))
                .thenAnswer(inv -> {
                    Entitlement received = inv.getArgument(0);
                    return EntitlementResponse.builder().key(received.getKey()).build();
                });

        mockMvc.perform(put("/api/governance/entitlements/correct.key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("correct.key"));
    }

    @Test
    void deleteEntitlement_returnsMessageResponse() throws Exception {
        mockMvc.perform(delete("/api/governance/entitlements/k"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Entitlement deleted"));

        verify(entitlementService).deleteEntitlement("k");
    }

    // ── Tier-entitlement values ────────────────────────────────────────

    @Test
    void getTierEntitlements_returnsMap() throws Exception {
        when(entitlementService.getTierValues("PERSONAL", "FREE"))
                .thenReturn(Map.of("a", "true", "b", "5"));

        mockMvc.perform(get("/api/governance/categories/PERSONAL/tiers/FREE/entitlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a").value("true"))
                .andExpect(jsonPath("$.b").value("5"));
    }

    @Test
    void setTierEntitlement_returnsMessageResponse() throws Exception {
        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("k").value("true").build();

        mockMvc.perform(put("/api/governance/categories/PERSONAL/tiers/FREE/entitlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tier entitlement updated"));

        verify(entitlementService).setTierEntitlement(eq("PERSONAL"), eq("FREE"),
                any(UpsertTierEntitlementRequest.class));
    }

    @Test
    void removeTierEntitlement_returnsMessageResponse() throws Exception {
        mockMvc.perform(delete("/api/governance/categories/PERSONAL/tiers/FREE/entitlements/k"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tier entitlement removed"));

        verify(entitlementService).removeTierEntitlement("PERSONAL", "FREE", "k");
    }
}
