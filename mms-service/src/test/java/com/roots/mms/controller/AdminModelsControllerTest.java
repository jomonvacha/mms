package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.dto.request.CreateAiModelRequest;
import com.roots.mms.dto.request.UpdateAiModelRequest;
import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelAudit;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.service.AiModelAuditService;
import com.roots.mms.service.AiModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminModelsControllerTest {

    private AiModelService service;
    private AiModelAuditService auditService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(AiModelService.class);
        auditService = mock(AiModelAuditService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminModelsController(service, auditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Providers ──────────────────────────────────────────────────────

    @Test
    void listProviders_delegates() throws Exception {
        when(service.listProviders()).thenReturn(List.of(
                AiModelProvider.builder().code("openai").displayName("OpenAI").build()));

        mockMvc.perform(get("/api/admin/models/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("openai"));
    }

    @Test
    void upsertProvider_newProvider_emitsProviderCreatedAudit() throws Exception {
        // No existing row → before-state is null → action should be PROVIDER_CREATED.
        when(service.listProviders()).thenReturn(List.of());
        when(service.upsertProvider(eq("openai"), eq("OpenAI"), eq("https://api.openai.com"), eq(true)))
                .thenReturn(AiModelProvider.builder().code("openai").build());

        Map<String, Object> body = Map.of(
                "displayName", "OpenAI",
                "baseUrl", "https://api.openai.com",
                "enabled", true);

        mockMvc.perform(put("/api/admin/models/providers/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.PROVIDER_CREATED),
                isNull(), isNull(), isNull(), any(AiModelProvider.class), any());
    }

    @Test
    void upsertProvider_existingProvider_emitsProviderUpdatedAudit() throws Exception {
        // Pre-existing row → audit captures before-snapshot → action is
        // PROVIDER_UPDATED. Verifies the controller's create-vs-update branch.
        AiModelProvider existing = AiModelProvider.builder().code("openai").displayName("Old").build();
        when(service.listProviders()).thenReturn(List.of(existing));
        when(auditService.serialize(existing)).thenReturn("{\"code\":\"openai\"}");
        when(service.upsertProvider(eq("openai"), any(), any(), any()))
                .thenReturn(AiModelProvider.builder().code("openai").build());

        mockMvc.perform(put("/api/admin/models/providers/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "New OpenAI"))))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.PROVIDER_UPDATED),
                isNull(), isNull(), eq("{\"code\":\"openai\"}"), any(AiModelProvider.class), any());
    }

    // ── Models ─────────────────────────────────────────────────────────

    @Test
    void list_returnsModels() throws Exception {
        when(service.listModels()).thenReturn(List.of(
                AiModel.builder().code("gpt-4o").build()));

        mockMvc.perform(get("/api/admin/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("gpt-4o"));
    }

    @Test
    void getOne_known_returnsModel() throws Exception {
        when(service.findModel("gpt-4o")).thenReturn(Optional.of(
                AiModel.builder().code("gpt-4o").displayName("GPT-4o").build()));

        mockMvc.perform(get("/api/admin/models/gpt-4o"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("GPT-4o"));
    }

    @Test
    void getOne_unknown_returns404() throws Exception {
        when(service.findModel("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/models/ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_invalidCodePattern_returns400() throws Exception {
        // Code regex enforced via @Valid on CreateAiModelRequest.
        CreateAiModelRequest req = new CreateAiModelRequest();
        req.setCode("UPPERCASE_BAD");  // pattern requires lowercase

        mockMvc.perform(post("/api/admin/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verify(service, never()).createModel(any(), any());
    }

    @Test
    void create_qualityLevelOutOfRange_returns400() throws Exception {
        CreateAiModelRequest req = new CreateAiModelRequest();
        req.setCode("gpt-4o");
        req.setQualityLevel(5); // @Max(4) violation

        mockMvc.perform(post("/api/admin/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_valid_savesAndAuditsModelCreated() throws Exception {
        CreateAiModelRequest req = new CreateAiModelRequest();
        req.setCode("gpt-4o");
        req.setProviderCode("openai");
        req.setDisplayName("GPT-4o");
        req.setQualityLevel(4);
        req.setCostLevel(3);

        AiModel saved = AiModel.builder().code("gpt-4o").providerCode("openai").build();
        when(service.createModel(any(AiModel.class), any())).thenReturn(saved);

        mockMvc.perform(post("/api/admin/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        var captor = forClass(AiModel.class);
        verify(service).createModel(captor.capture(), any());
        AiModel built = captor.getValue();
        assertThat(built.getCode()).isEqualTo("gpt-4o");
        assertThat(built.getQualityLevel()).isEqualTo(4);
        assertThat(built.getCostLevel()).isEqualTo(3);

        // Audit fired with no before-state (creation path).
        verify(auditService).record(eq(AiModelAudit.Action.MODEL_CREATED),
                eq("gpt-4o"), isNull(), isNull(), eq(saved), any());
    }

    @Test
    void update_capturesBeforeJson_thenAuditsUpdate() throws Exception {
        AiModel before = AiModel.builder().code("gpt-4o").displayName("Old").build();
        when(service.findModel("gpt-4o")).thenReturn(Optional.of(before));
        when(auditService.serialize(before)).thenReturn("{\"code\":\"gpt-4o\"}");
        when(service.updateModel(eq("gpt-4o"), any(AiModel.class), any()))
                .thenReturn(AiModel.builder().code("gpt-4o").displayName("New").build());

        UpdateAiModelRequest req = new UpdateAiModelRequest();
        req.setDisplayName("New");

        mockMvc.perform(put("/api/admin/models/gpt-4o")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.MODEL_UPDATED),
                eq("gpt-4o"), isNull(), eq("{\"code\":\"gpt-4o\"}"),
                any(AiModel.class), any());
    }

    @Test
    void setStatus_missingStatus_returns400_withoutAudit() throws Exception {
        mockMvc.perform(patch("/api/admin/models/gpt-4o/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notStatus", "x"))))
                .andExpect(status().isBadRequest());

        verify(service, never()).setStatus(any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void setStatus_invalidEnumValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/models/gpt-4o/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PARTIALLY_BROKEN"))))
                .andExpect(status().isBadRequest());

        verify(service, never()).setStatus(any(), any(), any());
    }

    @Test
    void setStatus_validEnum_caseInsensitive_andAudited() throws Exception {
        // Lowercase input must be accepted — the controller toUpperCase()s
        // before AiModel.Status.valueOf, so "enabled" → ENABLED works.
        AiModel before = AiModel.builder().code("gpt-4o").build();
        when(service.findModel("gpt-4o")).thenReturn(Optional.of(before));
        when(service.setStatus(eq("gpt-4o"), eq(AiModel.Status.ENABLED), any()))
                .thenReturn(before);

        mockMvc.perform(patch("/api/admin/models/gpt-4o/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "enabled"))))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.MODEL_STATUS_CHANGED),
                eq("gpt-4o"), isNull(), any(), any(AiModel.class), any());
    }

    @Test
    void delete_emitsModelDeletedAudit() throws Exception {
        AiModel before = AiModel.builder().code("gpt-4o").build();
        when(service.findModel("gpt-4o")).thenReturn(Optional.of(before));
        when(auditService.serialize(before)).thenReturn("{\"code\":\"gpt-4o\"}");

        mockMvc.perform(delete("/api/admin/models/gpt-4o"))
                .andExpect(status().isNoContent());

        verify(service).deleteModel("gpt-4o");
        verify(auditService).record(eq(AiModelAudit.Action.MODEL_DELETED),
                eq("gpt-4o"), isNull(), eq("{\"code\":\"gpt-4o\"}"), isNull(), any());
    }

    // ── Bindings ───────────────────────────────────────────────────────

    @Test
    void listBindings_delegates() throws Exception {
        when(service.listBindings("gpt-4o")).thenReturn(List.of(
                AiModelTierBinding.builder().categoryCode("PERSONAL").tierCode("FREE").build()));

        mockMvc.perform(get("/api/admin/models/gpt-4o/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tierCode").value("FREE"));
    }

    @Test
    void setBinding_newBinding_emitsBindingCreated() throws Exception {
        when(service.listBindings("gpt-4o")).thenReturn(List.of());
        AiModelTierBinding saved = AiModelTierBinding.builder()
                .categoryCode("PERSONAL").tierCode("FREE").build();
        when(service.setBinding(eq("gpt-4o"), eq("PERSONAL"), eq("FREE"),
                eq(true), any(), any())).thenReturn(saved);

        mockMvc.perform(put("/api/admin/models/gpt-4o/bindings/PERSONAL/FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isDefault", true))))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.BINDING_CREATED),
                eq("gpt-4o"), any(AiModelAudit.BindingRef.class),
                isNull(), eq(saved), any());
    }

    @Test
    void setBinding_existingBinding_emitsBindingUpdated() throws Exception {
        AiModelTierBinding existing = AiModelTierBinding.builder()
                .categoryCode("PERSONAL").tierCode("FREE").build();
        when(service.listBindings("gpt-4o")).thenReturn(List.of(existing));
        when(auditService.serialize(existing)).thenReturn("{\"category\":\"PERSONAL\"}");
        when(service.setBinding(any(), any(), any(), any(), any(), any())).thenReturn(existing);

        mockMvc.perform(put("/api/admin/models/gpt-4o/bindings/PERSONAL/FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(auditService).record(eq(AiModelAudit.Action.BINDING_UPDATED),
                eq("gpt-4o"), any(AiModelAudit.BindingRef.class),
                eq("{\"category\":\"PERSONAL\"}"), any(), any());
    }

    @Test
    void setBinding_emptyBody_acceptedWithNullFields() throws Exception {
        // Body is @RequestBody(required=false) — endpoint must handle missing
        // body gracefully. Used by toggle-default UI sending no payload.
        when(service.listBindings("gpt-4o")).thenReturn(List.of());
        when(service.setBinding(any(), any(), any(), isNull(), isNull(), any()))
                .thenReturn(AiModelTierBinding.builder().build());

        mockMvc.perform(put("/api/admin/models/gpt-4o/bindings/PERSONAL/FREE"))
                .andExpect(status().isOk());

        verify(service).setBinding(eq("gpt-4o"), eq("PERSONAL"), eq("FREE"),
                isNull(), isNull(), any());
    }

    @Test
    void removeBinding_emitsBindingDeleted() throws Exception {
        AiModelTierBinding existing = AiModelTierBinding.builder()
                .categoryCode("PERSONAL").tierCode("FREE").build();
        when(service.listBindings("gpt-4o")).thenReturn(List.of(existing));
        when(auditService.serialize(existing)).thenReturn("{\"binding\":\"x\"}");

        mockMvc.perform(delete("/api/admin/models/gpt-4o/bindings/PERSONAL/FREE"))
                .andExpect(status().isNoContent());

        verify(service).removeBinding("gpt-4o", "PERSONAL", "FREE");
        verify(auditService).record(eq(AiModelAudit.Action.BINDING_DELETED),
                eq("gpt-4o"), any(AiModelAudit.BindingRef.class),
                eq("{\"binding\":\"x\"}"), isNull(), any());
    }

    // ── Audit feed ─────────────────────────────────────────────────────

    @Test
    void audit_noFilters_returnsFullPaginated() throws Exception {
        Page<AiModelAudit> empty = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditService.findAll(any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/admin/models/audit"))
                .andExpect(status().isOk());

        verify(auditService).findAll(any(Pageable.class));
        verify(auditService, never()).findByModelCode(any(), any());
        verify(auditService, never()).findByActorId(any(), any());
    }

    @Test
    void audit_modelCodeFilter_routesToFindByModelCode() throws Exception {
        Page<AiModelAudit> empty = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditService.findByModelCode(eq("gpt-4o"), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/admin/models/audit").param("modelCode", "gpt-4o"))
                .andExpect(status().isOk());

        verify(auditService).findByModelCode(eq("gpt-4o"), any(Pageable.class));
    }

    @Test
    void audit_actorIdFilter_routesToFindByActorId() throws Exception {
        Page<AiModelAudit> empty = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditService.findByActorId(eq("admin@example.com"), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/admin/models/audit").param("actorId", "admin@example.com"))
                .andExpect(status().isOk());

        verify(auditService).findByActorId(eq("admin@example.com"), any(Pageable.class));
    }

    @Test
    void audit_clampsPageSizeUpperBound() throws Exception {
        // size is clamped to [1, 200]. Verifies the controller defends against
        // a 1M-row request that would exhaust memory.
        Page<AiModelAudit> empty = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditService.findAll(any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/admin/models/audit").param("size", "1000"))
                .andExpect(status().isOk());

        var captor = forClass(Pageable.class);
        verify(auditService).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void audit_clampsNegativePageToZero() throws Exception {
        Page<AiModelAudit> empty = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditService.findAll(any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/admin/models/audit").param("page", "-3"))
                .andExpect(status().isOk());

        var captor = forClass(Pageable.class);
        verify(auditService).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
    }
}
