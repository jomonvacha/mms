package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.dto.request.UserPreferencesRequest;
import com.roots.mms.entity.UserPreferences;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.repository.UserPreferencesRepository;
import com.roots.mms.security.services.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPreferencesControllerTest {

    private UserPreferencesRepository repo;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repo = mock(UserPreferencesRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserPreferencesController(repo))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private UUID authenticateUser() {
        UUID id = UUID.randomUUID();
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(id.toString());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
        return id;
    }

    @Test
    void getMyPreferences_unauthenticated_returns403() throws Exception {
        // No principal → AuthorizationException → 403 via global advice.
        mockMvc.perform(get("/api/user/preferences"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyPreferences_existing_returnsStoredValues() throws Exception {
        UUID id = authenticateUser();
        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(id);
        prefs.setTheme("dark");
        prefs.setLanguage("fr");
        prefs.setEmailNotifications(false);
        prefs.setNavbarDisplay("name");
        when(repo.findById(id)).thenReturn(Optional.of(prefs));

        mockMvc.perform(get("/api/user/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.language").value("fr"))
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.navbarDisplay").value("name"));
    }

    @Test
    void getMyPreferences_missing_returnsDefaults() throws Exception {
        // Brand-new user with no row in the table — controller fabricates a
        // default response rather than 404. UI relies on this to render
        // sensible defaults on first load.
        UUID id = authenticateUser();
        when(repo.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("system"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.emailNotifications").value(true))
                .andExpect(jsonPath("$.navbarDisplay").value("avatar"));
    }

    @Test
    void setMyPreferences_unauthenticated_returns403() throws Exception {
        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .theme("dark").language("en").build();
        mockMvc.perform(post("/api/user/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        verify(repo, never()).save(any());
    }

    @Test
    void setMyPreferences_invalidTheme_returns400() throws Exception {
        authenticateUser();
        // theme must match (system|light|dark) per @Pattern.
        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .theme("rainbow").language("en").build();

        mockMvc.perform(post("/api/user/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verify(repo, never()).save(any());
    }

    @Test
    void setMyPreferences_savesAllFields() throws Exception {
        UUID id = authenticateUser();
        when(repo.findById(id)).thenReturn(Optional.empty());
        when(repo.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .theme("dark").language("fr").country("FR").timezone("Europe/Paris")
                .emailNotifications(false).navbarDisplay("initials").build();

        mockMvc.perform(post("/api/user/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.country").value("FR"))
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.navbarDisplay").value("initials"));

        var captor = forClass(UserPreferences.class);
        verify(repo).save(captor.capture());
        UserPreferences saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(id);
        assertThat(saved.getTheme()).isEqualTo("dark");
    }

    @Test
    void setMyPreferences_invalidNavbar_normalizesToAvatar() throws Exception {
        // The @Pattern fires first and rejects truly invalid values, but the
        // controller has belt-and-suspenders logic: anything outside
        // (avatar|initials|name) is coerced to "avatar". Cover that branch by
        // sending a null navbarDisplay (passes validation, hits coercion).
        UUID id = authenticateUser();
        when(repo.findById(id)).thenReturn(Optional.empty());
        when(repo.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .theme("light").language("en").navbarDisplay(null).build();

        mockMvc.perform(post("/api/user/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.navbarDisplay").value("avatar"));
    }

    @Test
    void setMyPreferences_existingRow_updatesInPlace() throws Exception {
        UUID id = authenticateUser();
        UserPreferences existing = new UserPreferences();
        existing.setUserId(id);
        existing.setTheme("system");
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .theme("dark").language("en").build();

        mockMvc.perform(post("/api/user/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // No duplicate row created — same instance returned mutated.
        assertThat(existing.getTheme()).isEqualTo("dark");
        verify(repo).save(existing);
    }
}
