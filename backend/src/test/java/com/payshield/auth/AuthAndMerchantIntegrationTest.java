package com.payshield.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndMerchantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode loginAs(String email) throws Exception {
        String body = objectMapper.writeValueAsString(
                new AuthDtos.LoginRequest(email, DemoSeedRunner.DEMO_PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void loginRefreshAndLogoutFlowWorks() throws Exception {
        JsonNode loginJson = loginAs("admin@payshield.local");
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        // /me works with access token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@payshield.local"));

        // Refresh rotates token
        String refreshBody = objectMapper.writeValueAsString(new AuthDtos.RefreshRequest(refreshToken));
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String newRefresh = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // Old refresh token reuse is rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());

        // Logout revokes active refresh tokens
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDtos.RefreshRequest(newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPasswordReturnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(
                new AuthDtos.LoginRequest("admin@payshield.local", "WrongPassword123!"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void merchantIsolationPreventsCrossMerchantAccess() throws Exception {
        JsonNode acmeLogin = loginAs("acme.merchant@payshield.local");
        String acmeToken = acmeLogin.get("accessToken").asText();

        // Can access own merchant
        mockMvc.perform(get("/api/v1/merchants/22222222-2222-2222-2222-222222222201")
                        .header("Authorization", "Bearer " + acmeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantCode").value("MRC_ACME_RETAIL"));

        // Cannot access Nova merchant
        mockMvc.perform(get("/api/v1/merchants/22222222-2222-2222-2222-222222222202")
                        .header("Authorization", "Bearer " + acmeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MERCHANT_ISOLATION_VIOLATION"));

        // Cannot create merchants (PLATFORM_ADMIN only)
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + acmeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantCode\":\"MRC_ROGUE\",\"legalName\":\"Rogue\",\"displayName\":\"Rogue\",\"defaultCurrency\":\"INR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsHealthEndpointReturnsUp() throws Exception {
        JsonNode loginJson = loginAs("ops@payshield.local");
        String opsToken = loginJson.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/ops/health")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }
}
