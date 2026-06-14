package com.roots.mms.service;

import com.roots.mms.entity.AiModelAudit;
import com.roots.mms.repository.AiModelAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelAuditServiceTest {

    private AiModelAuditRepository repo;
    private AiModelAuditService service;

    @BeforeEach
    void setUp() {
        repo = mock(AiModelAuditRepository.class);
        service = new AiModelAuditService(repo);
    }

    @AfterEach
    void clearSecurityContext() {
        // record() reads SecurityContextHolder via SecurityUtils. Keep the
        // global static clean between tests to avoid cross-test bleed.
        SecurityContextHolder.clearContext();
    }

    // ── serialize ─────────────────────────────────────────────────────

    @Test
    void serialize_null_returnsNull() {
        assertThat(service.serialize(null)).isNull();
    }

    @Test
    void serialize_simpleObject_returnsJson() {
        var json = service.serialize(java.util.Map.of("a", 1, "b", "x"));

        assertThat(json).contains("\"a\":1").contains("\"b\":\"x\"");
    }

    @Test
    void serialize_omitsNullFields() {
        // Service is configured with Include.NON_NULL.
        AiModelAudit.BindingRef ref = AiModelAudit.BindingRef.builder()
                .categoryCode("chat").tierCode(null).build();

        String json = service.serialize(ref);

        assertThat(json).contains("categoryCode").doesNotContain("tierCode");
    }

    @Test
    void serialize_unserializable_returnsNull() {
        // A self-referential object Jackson can't handle without recursion guard.
        Object cyclic = new Object() {
            @SuppressWarnings("unused")
            public Object self = this;
        };

        assertThat(service.serialize(cyclic)).isNull();
    }

    // ── record ────────────────────────────────────────────────────────

    @Test
    void record_anonymousActor_writesAuditWithNullActor() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("User-Agent")).thenReturn("ua");
        when(req.getRemoteAddr()).thenReturn("10.0.0.1");

        service.record(AiModelAudit.Action.MODEL_CREATED, "gpt-4",
                null, null, java.util.Map.of("k", "v"), req);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        AiModelAudit saved = captor.getValue();
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getAction()).isEqualTo(AiModelAudit.Action.MODEL_CREATED);
        assertThat(saved.getModelCode()).isEqualTo("gpt-4");
        assertThat(saved.getAfterJson()).contains("\"k\":\"v\"");
        assertThat(saved.getBeforeJson()).isNull();
        assertThat(saved.getUserAgent()).isEqualTo("ua");
        assertThat(saved.getIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getAt()).isNotNull();
    }

    @Test
    void record_xForwardedFor_takesFirstIp() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.2");
        when(req.getHeader("User-Agent")).thenReturn("agent");

        service.record(AiModelAudit.Action.MODEL_DELETED, "m", null, "{\"x\":1}", null, req);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.1");
        assertThat(captor.getValue().getBeforeJson()).isEqualTo("{\"x\":1}");
        assertThat(captor.getValue().getAfterJson()).isNull();
    }

    @Test
    void record_xForwardedForBlank_fallsBackToRemoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        service.record(AiModelAudit.Action.MODEL_UPDATED, "m", null, null, null, req);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void record_xForwardedForSingleIp_returnedTrimmed() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        service.record(AiModelAudit.Action.MODEL_UPDATED, "m", null, null, null, req);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.5");
    }

    @Test
    void record_nullRequest_doesNotNpe() {
        service.record(AiModelAudit.Action.PROVIDER_CREATED, "openai",
                null, null, null, null);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        AiModelAudit saved = captor.getValue();
        assertThat(saved.getIp()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    void record_repoFailure_isSwallowed() {
        // Storage failure must never propagate out of record() — that's the
        // contract called out in the service Javadoc.
        doThrow(new RuntimeException("db down")).when(repo).save(any(AiModelAudit.class));

        service.record(AiModelAudit.Action.MODEL_CREATED, "m", null, null, null, null);

        verify(repo).save(any(AiModelAudit.class));
    }

    @Test
    void record_includesBindingRef_whenProvided() {
        AiModelAudit.BindingRef binding = AiModelAudit.BindingRef.builder()
                .categoryCode("chat").tierCode("free").build();

        service.record(AiModelAudit.Action.BINDING_CREATED, "gpt-4",
                binding, null, null, null);

        var captor = forClass(AiModelAudit.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getBinding()).isSameAs(binding);
    }

    // ── reads ─────────────────────────────────────────────────────────

    @Test
    void findAll_delegatesToRepo() {
        Pageable p = PageRequest.of(0, 10);
        Page<AiModelAudit> page = new PageImpl<>(List.of());
        when(repo.findAllByOrderByAtDesc(p)).thenReturn(page);

        assertThat(service.findAll(p)).isSameAs(page);
    }

    @Test
    void findByModelCode_delegates() {
        Pageable p = PageRequest.of(0, 5);
        Page<AiModelAudit> page = new PageImpl<>(List.of());
        when(repo.findByModelCodeOrderByAtDesc("gpt-4", p)).thenReturn(page);

        assertThat(service.findByModelCode("gpt-4", p)).isSameAs(page);
    }

    @Test
    void findByActorId_delegates() {
        Pageable p = PageRequest.of(0, 5);
        Page<AiModelAudit> page = new PageImpl<>(List.of());
        when(repo.findByActorIdOrderByAtDesc("admin@example.com", p)).thenReturn(page);

        assertThat(service.findByActorId("admin@example.com", p)).isSameAs(page);
        verify(repo, never()).findAllByOrderByAtDesc(any());
    }
}
