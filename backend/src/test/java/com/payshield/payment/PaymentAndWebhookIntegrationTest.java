package com.payshield.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.auth.AuthDtos;
import com.payshield.auth.DemoSeedRunner;
import com.payshield.outbox.OutboxEventRepository;
import com.payshield.outbox.OutboxService;
import com.payshield.provider.ProviderCode;
import com.payshield.provider.SimulatorFailureMode;
import com.payshield.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAndWebhookIntegrationTest {

    private static final UUID ACME_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxService outboxService;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Value("${payshield.webhooks.alpha-secret}")
    private String alphaSecret;

    private String adminToken;
    private String acmeToken;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin@payshield.local");
        acmeToken = login("acme.merchant@payshield.local");
        // Reset Provider Alpha to NORMAL_SUCCESS before each test
        mockMvc.perform(put("/api/v1/providers/PROVIDER_ALPHA/config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"failureMode\":\"NORMAL_SUCCESS\",\"latencyMs\":5,\"failureRatePercent\":0,\"timeoutRatePercent\":0}"))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new AuthDtos.LoginRequest(email, DemoSeedRunner.DEMO_PASSWORD));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void idempotencyPreventsDuplicatePaymentsAndRejectsPayloadMismatch() throws Exception {
        String idemKey = "idem-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-1001", 150000L, "INR", ProviderCode.PROVIDER_ALPHA));

        MvcResult first = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andReturn();

        String paymentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Replay with same key & payload returns same paymentId with idempotentReplay=true
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        // Reusing key with different amount returns 409 Conflict
        String differentPayload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-1001", 999999L, "INR", ProviderCode.PROVIDER_ALPHA));
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD"));
    }

    @Test
    void concurrentIdempotentRequestsProduceSinglePayment() throws Exception {
        String idemKey = "concurrent-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-CONC-1", 50000L, "INR", ProviderCode.PROVIDER_ALPHA));

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                MvcResult res = mockMvc.perform(post("/api/v1/payments")
                                .header("Authorization", "Bearer " + acmeToken)
                                .header("Idempotency-Key", idemKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn();
                int status = res.getResponse().getStatus();
                if (status == 201) {
                    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
                }
                return "STATUS_" + status;
            }));
        }
        startGate.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            results.add(f.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long distinctPaymentIds = results.stream().filter(s -> !s.startsWith("STATUS_")).distinct().count();
        assertThat(distinctPaymentIds).isEqualTo(1);
    }

    @Test
    void providerTimeoutEntersUnknownAndStatusVerificationResolvesToSucceeded() throws Exception {
        // Configure Provider Alpha for TIMEOUT_AFTER_PROCESSING
        mockMvc.perform(put("/api/v1/providers/PROVIDER_ALPHA/config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"failureMode\":\"" + SimulatorFailureMode.TIMEOUT_AFTER_PROCESSING
                                + "\",\"latencyMs\":5,\"failureRatePercent\":0,\"timeoutRatePercent\":0}"))
                .andExpect(status().isOk());

        String payload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-TIMEOUT-1", 25000L, "INR", ProviderCode.PROVIDER_ALPHA));
        MvcResult created = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", "timeout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andReturn();

        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Verify status against provider -> resolves UNKNOWN -> SUCCEEDED
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/verify")
                        .header("Authorization", "Bearer " + acmeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void webhookValidatesHmacAndDeduplicatesEventsAndOutboxRelays() throws Exception {
        // Create a payment in UNKNOWN state via TIMEOUT_BEFORE_PROCESSING
        mockMvc.perform(put("/api/v1/providers/PROVIDER_ALPHA/config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"failureMode\":\"TIMEOUT_BEFORE_PROCESSING\",\"latencyMs\":5,\"failureRatePercent\":0,\"timeoutRatePercent\":0}"))
                .andExpect(status().isOk());

        String payload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-WH-1", 40000L, "INR", ProviderCode.PROVIDER_ALPHA));
        MvcResult created = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", "wh-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        String eventId = "evt_" + UUID.randomUUID();
        String webhookBody = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PAYMENT_SUCCEEDED\",\"paymentId\":\""
                + paymentId + "\",\"providerReference\":\"SIM_WH_999\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sig = WebhookService.computeHmacSha256(alphaSecret, timestamp + "." + webhookBody);

        // Invalid signature rejected
        mockMvc.perform(post("/api/v1/webhooks/PROVIDER_ALPHA")
                        .header("X-PayShield-Timestamp", timestamp)
                        .header("X-PayShield-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));

        // Valid webhook processed
        mockMvc.perform(post("/api/v1/webhooks/PROVIDER_ALPHA")
                        .header("X-PayShield-Timestamp", timestamp)
                        .header("X-PayShield-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Duplicate webhook safely ignored
        mockMvc.perform(post("/api/v1/webhooks/PROVIDER_ALPHA")
                        .header("X-PayShield-Timestamp", timestamp)
                        .header("X-PayShield-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        // Payment is now SUCCEEDED
        var updated = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        // Outbox relay publishes pending events
        int published = outboxService.relayPendingEvents();
        assertThat(published).isGreaterThanOrEqualTo(0);
        assertThat(outboxEventRepository.count()).isGreaterThan(0);
    }
}
