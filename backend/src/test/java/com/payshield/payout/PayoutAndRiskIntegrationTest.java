package com.payshield.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.auth.AuthDtos;
import com.payshield.auth.DemoSeedRunner;
import com.payshield.risk.RiskAlertEntity;
import com.payshield.risk.RiskAlertRepository;
import com.payshield.risk.RiskEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayoutAndRiskIntegrationTest {

    private static final UUID ACME_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID ALLOWLISTED_BEN = UUID.fromString("55555555-5555-5555-5555-555555555501");
    private static final UUID UNVERIFIED_BEN = UUID.fromString("55555555-5555-5555-5555-555555555502");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RiskEngineService riskEngineService;
    @Autowired private RiskAlertRepository riskAlertRepository;

    private String adminToken;
    private String makerToken;
    private String checkerToken;
    private String riskToken;

    @BeforeEach
    void setup() throws Exception {
        adminToken = login("admin@payshield.local");
        makerToken = login("maker@payshield.local");
        checkerToken = login("checker@payshield.local");
        riskToken = login("risk@payshield.local");

        // Ensure kill switch is off before each test
        mockMvc.perform(put("/api/v1/payouts/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"Test reset\"}"))
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
    void makerCheckerSeparationAndUnauthorizedExecutionRejected() throws Exception {
        String createBody = objectMapper.writeValueAsString(new PayoutService.CreatePayoutRequest(
                ACME_ID, ALLOWLISTED_BEN, 150000L, "INR", "Vendor invoice #881"));

        MvcResult created = mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_MAKER_CHECKER"))
                .andReturn();
        String payoutId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Direct execution before approval is rejected
        mockMvc.perform(post("/api/v1/payouts/" + payoutId + "/execute")
                        .header("Authorization", "Bearer " + checkerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_NOT_APPROVED"));

        // Maker cannot approve their own payout (even if we test with admin/maker mismatch; here maker lacks CHECKER role too, so test with admin creating & trying to self-approve!)
        MvcResult adminCreated = mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String adminPayoutId = objectMapper.readTree(adminCreated.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/payouts/" + adminPayoutId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comments\":\"Self approval attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SEPARATION_OF_DUTIES_VIOLATION"));

        // Checker approves maker's payout -> Execute succeeds
        mockMvc.perform(post("/api/v1/payouts/" + payoutId + "/decision")
                        .header("Authorization", "Bearer " + checkerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"comments\":\"Verified invoice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/payouts/" + payoutId + "/execute")
                        .header("Authorization", "Bearer " + checkerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    void nonAllowlistedBeneficiaryAndKillSwitchAreEnforced() throws Exception {
        String badBenBody = objectMapper.writeValueAsString(new PayoutService.CreatePayoutRequest(
                ACME_ID, UNVERIFIED_BEN, 50000L, "INR", "Unverified vendor"));
        mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBenBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BENEFICIARY_NOT_ALLOWLISTED"));

        // Activate kill switch
        mockMvc.perform(put("/api/v1/payouts/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"Emergency freeze\"}"))
                .andExpect(status().isOk());

        String validBody = objectMapper.writeValueAsString(new PayoutService.CreatePayoutRequest(
                ACME_ID, ALLOWLISTED_BEN, 50000L, "INR", "Blocked by kill switch"));
        mockMvc.perform(post("/api/v1/payouts")
                        .header("Authorization", "Bearer " + makerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_KILL_SWITCH_ACTIVE"));
    }

    @Test
    void riskEngineGeneratesExplainableAlertAndAnalystResolvesIt() throws Exception {
        long beforeCount = riskAlertRepository.count();
        riskEngineService.evaluatePayment("pay-high-1", ACME_ID, 25000000L, "SUCCEEDED");
        assertThat(riskAlertRepository.count()).isGreaterThan(beforeCount);

        RiskAlertEntity alert = riskAlertRepository.findAll().stream()
                .filter(a -> "pay-high-1".equals(a.getEntityId()))
                .findFirst()
                .orElseThrow();
        assertThat(alert.getExplanation()).contains("exceeded threshold");

        mockMvc.perform(patch("/api/v1/risk/alerts/" + alert.getId())
                        .header("Authorization", "Bearer " + riskToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"analystNotes\":\"Verified legitimate B2B order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
