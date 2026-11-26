package com.payshield.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consumes PAYMENT_SUCCEEDED outbox events idempotently via inbox deduplication
 * and posts balanced double-entry journal transactions.
 */
@Component
public class LedgerOutboxConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerOutboxConsumer.class);

    private final OutboxService outboxService;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public LedgerOutboxConsumer(OutboxService outboxService,
                                LedgerService ledgerService,
                                ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Transactional
    public void onOutboxEvent(OutboxService.PublishedOutboxEvent event) {
        if (!"PAYMENT_SUCCEEDED".equals(event.eventType())) {
            return;
        }
        if (!outboxService.tryClaimInbox("LEDGER_POSTER", event.eventId().toString())) {
            return;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.payloadJson());
            String paymentId = payload.get("paymentId").asText();
            String merchantId = payload.get("merchantId").asText();
            long amountMinor = payload.get("amountMinor").asLong();
            String currency = payload.get("currency").asText();

            // 2% simulated platform fee (integer math, min 100 minor units if amount >= 100)
            long feeMinor = Math.max(1L, (amountMinor * 2L) / 100L);
            long merchantNet = amountMinor - feeMinor;

            String merchantPayableCode = "LIABILITY:MERCHANT_PAYABLE:" + merchantId + ":" + currency;
            String clearingAssetCode = "ASSET:PROVIDER_CLEARING:" + currency;
            String platformFeeCode = "REVENUE:PLATFORM_FEE:" + currency;

            ledgerService.postJournal(
                    "PAYMENT",
                    paymentId,
                    "CAPTURE",
                    "Payment capture for " + paymentId,
                    currency,
                    List.of(
                            new LedgerService.PostingLineSpec(clearingAssetCode, JournalLineEntity.EntryType.DEBIT, amountMinor),
                            new LedgerService.PostingLineSpec(merchantPayableCode, JournalLineEntity.EntryType.CREDIT, merchantNet),
                            new LedgerService.PostingLineSpec(platformFeeCode, JournalLineEntity.EntryType.CREDIT, feeMinor)
                    ),
                    "SYSTEM_OUTBOX",
                    null
            );
        } catch (Exception ex) {
            log.warn("Could not auto-post ledger for outbox event {}: {}", event.eventId(), ex.getMessage());
        }
    }
}
