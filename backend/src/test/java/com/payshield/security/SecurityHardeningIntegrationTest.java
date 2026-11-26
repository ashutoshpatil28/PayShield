package com.payshield.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.auth.AuthDtos;
import com.payshield.auth.DemoSeedRunner;
import com.payshield.payment.PaymentDtos;
import com.payshield.payout.PayoutService;
import com.payshield.provider.ProviderCode;
import com.payshield.webhook.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security verification suite covering authentication, RBAC, IDOR, privilege escalation,
 * stolen provider credential simulation, webhook replay, and credential revocation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningIntegrationTest {

    private static final UUID ACME_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID NOVA_ID = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID ACME_ALLOWLISTED_BEN = UUID.fromString("55555555-5555-5555-5555-555555555501");
    private static final UUID MAKER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333306");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Value("${payshield.webhooks.alpha-secret}")
    private String alphaWebhookSecret;

    private JsonNode login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new AuthDtos.LoginRequest(email, DemoSeedRunner.DEMO_PASSWORD));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void stolenProviderCredentialCannotBypassAuthOrApprovalWorkflow() throws Exception {
        // 1. Unauthenticated caller presenting a stolen provider HMAC secret cannot execute payouts
        mockMvc.perform(post("/api/v1/payouts/" + UUID.randomUUID() + "/execute")
                        .header("X-Provider-Api-Key", alphaWebhookSecret))
                .andExpect(status().isUnauthorized());

        // 2. Even an authenticated checker cannot execute a payout that is still PENDING_MAKER_CHECKER
        String makerToken = login("maker@payshield.local").get("accessToken").asText();
        String checkerToken = login("checker@payshield.local").get("accessToken").asText();

        String createBody = objectMapper.writeValueAsString(new PayoutService.CreatePayoutRequest(
                ACME_ID, ACME_ALLOWLISTED_BEN, 50000L, "INR", "Security bypass check"));
        MvcResult created = mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String payoutId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/payouts/" + payoutId + "/execute")
                        .header("Authorization", "Bearer " + checkerToken)
                        .header("X-Provider-Api-Key", alphaWebhookSecret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_NOT_APPROVED"));
    }

    @Test
    void idorCrossMerchantPaymentAndBeneficiaryAccessBlocked() throws Exception {
        String acmeToken = login("acme.merchant@payshield.local").get("accessToken").asText();
        String novaToken = login("nova.merchant@payshield.local").get("accessToken").asText();

        String payBody = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-IDOR-1", 10000L, "INR", ProviderCode.PROVIDER_ALPHA));
        MvcResult payRes = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", "idor-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody))
                .andExpect(status().isCreated())
                .andReturn();
        String acmePaymentId = objectMapper.readTree(payRes.getResponse().getContentAsString()).get("id").asText();

        // Nova merchant admin cannot read Acme's payment (IDOR)
        mockMvc.perform(get("/api/v1/payments/" + acmePaymentId)
                        .header("Authorization", "Bearer " + novaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MERCHANT_ISOLATION_VIOLATION"));

        // Nova merchant admin cannot initiate payout for Acme's beneficiary
        String crossPayout = objectMapper.writeValueAsString(new PayoutService.CreatePayoutRequest(
                NOVA_ID, ACME_ALLOWLISTED_BEN, 10000L, "INR", "IDOR payout attempt"));
        mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + novaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossPayout))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BENEFICIARY_MERCHANT_MISMATCH"));
    }

    @Test
    void privilegeEscalationAttemptsRejected() throws Exception {
        String makerToken = login("maker@payshield.local").get("accessToken").asText();

        // Maker cannot grant themselves PLATFORM_ADMIN
        mockMvc.perform(put("/api/v1/admin/users/" + MAKER_USER_ID + "/roles")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"PLATFORM_ADMIN\"]}"))
                .andExpect(status().isForbidden());

        // Maker cannot toggle global kill switch
        mockMvc.perform(put("/api/v1/payouts/kill-switch")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());

        // Maker cannot approve payouts
        mockMvc.perform(post("/api/v1/payouts/" + UUID.randomUUID() + "/decision")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookReplayOutsideFreshnessWindowIsRejected() throws Exception {
        String staleTimestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        String body = "{\"eventId\":\"evt_stale_1\",\"eventType\":\"PAYMENT_SUCCEEDED\",\"paymentId\":\""
                + UUID.randomUUID() + "\"}";
        String sig = WebhookService.computeHmacSha256(alphaWebhookSecret, staleTimestamp + "." + body);

        mockMvc.perform(post("/api/v1/webhooks/PROVIDER_ALPHA")
                        .header("X-PayShield-Timestamp", staleTimestamp)
                        .header("X-PayShield-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("WEBHOOK_TIMESTAMP_EXPIRED"));
    }

    @Test
    void adminCredentialRevocationInvalidatesRefreshTokens() throws Exception {
        String adminToken = login("admin@payshield.local").get("accessToken").asText();
        JsonNode makerLogin = login("maker@payshield.local");
        String makerRefresh = makerLogin.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/admin/users/" + MAKER_USER_ID + "/revoke-sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(MAKER_USER_ID.toString()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDtos.RefreshRequest(makerRefresh))))
                .andExpect(status().isUnauthorized());
    }
}
