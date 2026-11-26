package com.payshield.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.auth.AuthDtos;
import com.payshield.auth.DemoSeedRunner;
import com.payshield.common.DomainException;
import com.payshield.payment.PaymentDtos;
import com.payshield.provider.ProviderCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerAndReconciliationTest {

    private static final UUID ACME_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");

    @Autowired private LedgerService ledgerService;
    @Autowired private JournalTransactionRepository journalTxRepository;
    @Autowired private com.payshield.reconciliation.ReconciliationCaseRepository reconCaseRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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
    void rejectsUnbalancedJournalTransaction() {
        assertThatThrownBy(() -> ledgerService.postJournal(
                "TEST", UUID.randomUUID().toString(), "UNBALANCED",
                "Unbalanced test", "INR",
                List.of(
                        new LedgerService.PostingLineSpec("ASSET:PROVIDER_CLEARING:INR", JournalLineEntity.EntryType.DEBIT, 10000L),
                        new LedgerService.PostingLineSpec("REVENUE:PLATFORM_FEE:INR", JournalLineEntity.EntryType.CREDIT, 9000L)
                ),
                "test@payshield.local", null
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must equal credits");
    }

    @Test
    void preventsDuplicatePostingReferenceAndSupportsReversal() {
        String sourceId = UUID.randomUUID().toString();
        var tx1 = ledgerService.postJournal(
                "MANUAL", sourceId, "TEST_POST", "Balanced posting", "INR",
                List.of(
                        new LedgerService.PostingLineSpec("ASSET:PROVIDER_CLEARING:INR", JournalLineEntity.EntryType.DEBIT, 50000L),
                        new LedgerService.PostingLineSpec("REVENUE:PLATFORM_FEE:INR", JournalLineEntity.EntryType.CREDIT, 50000L)
                ),
                "admin@payshield.local", null
        );
        // Same posting reference returns original idempotently
        var tx2 = ledgerService.postJournal(
                "MANUAL", sourceId, "TEST_POST", "Balanced posting", "INR",
                List.of(
                        new LedgerService.PostingLineSpec("ASSET:PROVIDER_CLEARING:INR", JournalLineEntity.EntryType.DEBIT, 50000L),
                        new LedgerService.PostingLineSpec("REVENUE:PLATFORM_FEE:INR", JournalLineEntity.EntryType.CREDIT, 50000L)
                ),
                "admin@payshield.local", null
        );
        assertThat(tx2.id()).isEqualTo(tx1.id());

        // Reversal creates new balanced transaction pointing to original
        var rev = ledgerService.reverseTransaction(
                tx1.id(), "Correction test", UUID.randomUUID(), "admin@payshield.local", "PLATFORM_ADMIN", "127.0.0.1");
        assertThat(rev.reversalOfTxId()).isEqualTo(tx1.id());
        assertThat(rev.lines()).hasSize(2);
    }

    @Test
    void financialAuditorCannotReverseLedgerTransactions() throws Exception {
        String auditorToken = login("auditor@payshield.local");
        mockMvc.perform(post("/api/v1/ledger/transactions/" + UUID.randomUUID() + "/reverse")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Auditor attempt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliationDetectsAmountMismatchAndUnknownReference() throws Exception {
        String acmeToken = login("acme.merchant@payshield.local");
        String opsToken = login("ops@payshield.local");

        // Create a real succeeded payment
        String payload = objectMapper.writeValueAsString(new PaymentDtos.CreatePaymentRequest(
                ACME_ID, "ORD-RECON-1", 10000L, "INR", ProviderCode.PROVIDER_ALPHA));
        MvcResult payRes = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + acmeToken)
                        .header("Idempotency-Key", "recon-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String providerRef = objectMapper.readTree(payRes.getResponse().getContentAsString())
                .get("providerReference").asText();

        // Snapshot ledger balances and transaction count BEFORE reconciliation
        long txCountBefore = journalTxRepository.count();
        var balancesBefore = ledgerService.listBalances().stream()
                .collect(java.util.stream.Collectors.toMap(
                        LedgerService.AccountBalanceDto::accountCode,
                        LedgerService.AccountBalanceDto::balanceMinor));

        // Upload CSV with amount mismatch, fee mismatch (duplicate ref), and unknown ref
        String csv = "provider_reference,merchant_order_ref,amount_minor,fee_minor,currency,settlement_date\n"
                + providerRef + ",ORD-RECON-1,9500,200,INR,2026-11-26\n"
                + providerRef + ",ORD-RECON-1,10000,999,INR,2026-11-26\n"
                + "SIM_UNKNOWN_REF_404,ORD-MISSING,5000,100,INR,2026-11-26\n";

        String reqBody = objectMapper.writeValueAsString(new com.payshield.reconciliation.ReconciliationController.UploadCsvRequest(
                "settlement_20261126.csv", ProviderCode.PROVIDER_ALPHA, csv));

        mockMvc.perform(post("/api/v1/reconciliation/files")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.totalRecords").value(3))
                .andExpect(jsonPath("$.file.unmatchedRecords").value(3))
                .andExpect(jsonPath("$.casesCreated").value(3));

        // Verify ledger was NEVER silently altered by reconciliation discrepancies
        assertThat(journalTxRepository.count()).isEqualTo(txCountBefore);
        var balancesAfter = ledgerService.listBalances().stream()
                .collect(java.util.stream.Collectors.toMap(
                        LedgerService.AccountBalanceDto::accountCode,
                        LedgerService.AccountBalanceDto::balanceMinor));
        assertThat(balancesAfter).isEqualTo(balancesBefore);
        assertThat(reconCaseRepository.count()).isGreaterThanOrEqualTo(3);

        // Verify financial summary confirms SUM(DEBIT) == SUM(CREDIT)
        mockMvc.perform(get("/api/v1/ledger/summary")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));

        // Duplicate CSV upload is rejected
        mockMvc.perform(post("/api/v1/reconciliation/files")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_SETTLEMENT_FILE"));
    }
}
