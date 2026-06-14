package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.MembershipTypeConfig;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipTypeConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MembershipTypeControllerTest {

    private MembershipTypeConfigRepository repo;
    private MemberRepository memberRepo;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repo = mock(MembershipTypeConfigRepository.class);
        memberRepo = mock(MemberRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MembershipTypeController(repo, memberRepo))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsAllTypes() throws Exception {
        when(repo.findAll()).thenReturn(List.of(
                MembershipTypeConfig.builder().code("PERSONAL").displayName("Personal").build()));

        mockMvc.perform(get("/api/membership-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PERSONAL"));
    }

    @Test
    void create_normalizesCodeToUppercaseSnake() throws Exception {
        // The controller normalizes "my new!type" → "MY_NEW_TYPE" before
        // checking for duplicates and saving. This is critical because the
        // unique constraint is on the normalized form.
        when(repo.findByCode("MY_NEW_TYPE")).thenReturn(Optional.empty());
        when(repo.save(any(MembershipTypeConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> body = Map.of("code", "  my new!type  ", "displayName", "My Type");
        mockMvc.perform(post("/api/membership-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MY_NEW_TYPE"));

        var captor = forClass(MembershipTypeConfig.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("MY_NEW_TYPE");
        assertThat(captor.getValue().getSystem()).isFalse();
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        // Whitespace-only code normalizes to "" and must be rejected.
        Map<String, String> body = Map.of("code", "   ");

        mockMvc.perform(post("/api/membership-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(repo.findByCode("EXISTING")).thenReturn(Optional.of(
                MembershipTypeConfig.builder().code("EXISTING").build()));

        mockMvc.perform(post("/api/membership-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "existing"))))
                .andExpect(status().isConflict());

        verify(repo, never()).save(any());
    }

    @Test
    void update_partialPatch_appliesOnlyPresentKeys() throws Exception {
        UUID id = UUID.randomUUID();
        MembershipTypeConfig existing = MembershipTypeConfig.builder()
                .id(id).code("X").displayName("Old").description("OldDesc").build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(MembershipTypeConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Only displayName in patch — description must remain "OldDesc".
        Map<String, String> body = Map.of("displayName", "New");
        mockMvc.perform(put("/api/membership-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(existing.getDisplayName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("OldDesc");
    }

    @Test
    void delete_systemType_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(MembershipTypeConfig.builder()
                .id(id).code("PERSONAL").system(true).build()));

        mockMvc.perform(delete("/api/membership-types/" + id))
                .andExpect(status().isBadRequest());

        verify(repo, never()).delete(any());
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/membership-types/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_typeWithNoMembers_succeeds() throws Exception {
        UUID id = UUID.randomUUID();
        MembershipTypeConfig t = MembershipTypeConfig.builder()
                .id(id).code("CUSTOM").system(false).build();
        when(repo.findById(id)).thenReturn(Optional.of(t));
        when(memberRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(delete("/api/membership-types/" + id))
                .andExpect(status().isNoContent());

        verify(repo).delete(t);
    }
}
