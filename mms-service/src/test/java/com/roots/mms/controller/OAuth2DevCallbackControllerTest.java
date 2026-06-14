package com.roots.mms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OAuth2DevCallbackControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuth2DevCallbackController()).build();
    }

    @Test
    void callback_rendersHtmlWithBothTokensInjected() throws Exception {
        mockMvc.perform(get("/oauth2/callback-dev")
                        .param("token", "ACCESS_TOKEN_VALUE")
                        .param("refreshToken", "REFRESH_TOKEN_VALUE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("ACCESS_TOKEN_VALUE")))
                .andExpect(content().string(containsString("REFRESH_TOKEN_VALUE")))
                .andExpect(content().string(containsString("OAuth2 Tokens (DEV)")));
    }

    @Test
    void callback_missingParams_rendersEmptyTokenFields() throws Exception {
        // Both params optional — rendering must not blow up. The nvl helper
        // converts nulls to empty strings.
        mockMvc.perform(get("/oauth2/callback-dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OAuth2 Tokens (DEV)")))
                // The literal token "null" must NOT appear in the output —
                // confirms nvl() ran rather than String.valueOf.
                .andExpect(content().string(not(containsString(">null<"))));
    }

    @Test
    void callback_onlyAccessToken_rendersWithEmptyRefresh() throws Exception {
        mockMvc.perform(get("/oauth2/callback-dev").param("token", "ONLY_ACCESS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ONLY_ACCESS")));
    }
}
