package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.Feature;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.FeatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
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

class FeatureControllerTest {

    private FeatureService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(FeatureService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FeatureController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void myFeatures_extractsRolesFromAuthorities_andDelegates() throws Exception {
        // Authenticated user with two roles. Controller flattens authorities
        // to role names and asks the service for the features visible to that
        // role set.
        UserPrincipal principal = mock(UserPrincipal.class);
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"));
        when(principal.getAuthorities()).thenAnswer(inv -> authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", authorities));

        Feature f = Feature.builder().code("foo").name("Foo").build();
        when(service.getFeaturesForRoles(any())).thenReturn(List.of(f));

        mockMvc.perform(get("/api/features/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("foo"));

        @SuppressWarnings("unchecked")
        var captor = forClass(List.class);
        verify(service).getFeaturesForRoles(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("ROLE_MEMBER", "ROLE_ADMIN");
    }

    @Test
    void myFeatures_unauthenticated_returnsEmptyList() throws Exception {
        // No principal → empty role list → service returns whatever it does
        // for the empty set (typically empty). Controller does NOT short-circuit
        // here — it lets the service decide.
        when(service.getFeaturesForRoles(eq(List.of()))).thenReturn(List.of());

        mockMvc.perform(get("/api/features/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listAll_returnsList() throws Exception {
        when(service.listAll()).thenReturn(List.of(
                Feature.builder().code("a").name("A").build()));

        mockMvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("a"));
    }

    @Test
    void create_passesFeatureToService() throws Exception {
        Feature f = Feature.builder().code("foo").name("Foo").build();
        when(service.create(any(Feature.class))).thenReturn(f);

        mockMvc.perform(post("/api/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("foo"));
    }

    @Test
    void update_passesIdAndPatch() throws Exception {
        String id = UUID.randomUUID().toString();
        Feature updated = Feature.builder().code("foo").name("Foo Updated").build();
        when(service.update(eq(id), any(Feature.class))).thenReturn(updated);

        mockMvc.perform(put("/api/features/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Foo Updated"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(delete("/api/features/" + id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void getMatrix_returnsRoleToFeatureMap() throws Exception {
        when(service.getMatrix()).thenReturn(Map.of(
                "ROLE_ADMIN", Set.of("a", "b"),
                "ROLE_MEMBER", Set.of("c")));

        mockMvc.perform(get("/api/features/matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ROLE_ADMIN").isArray())
                .andExpect(jsonPath("$.ROLE_MEMBER[0]").value("c"));
    }

    @Test
    void setRoleFeatures_passesRoleAndCodeSet() throws Exception {
        mockMvc.perform(put("/api/features/matrix/ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Set.of("foo", "bar"))))
                .andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        var captor = forClass(Set.class);
        verify(service).setRoleFeatures(eq("ROLE_ADMIN"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("foo", "bar");
    }
}
