package com.payshield.payment;

import com.payshield.common.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages idempotency_records in isolated REQUIRES_NEW transactions so concurrent
 * requests never taint the caller's Hibernate session on unique constraint races.
 */
@Service
public class IdempotencyLockService {

    private final IdempotencyRecordRepository idempotencyRepository;

    public IdempotencyLockService(IdempotencyRecordRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    public record ClaimResult(IdempotencyRecordEntity record, boolean alreadyExisted) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claimOrGet(UUID merchantId, String idempotencyKey, String requestHash) {
        var existing = idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return new ClaimResult(validateHash(existing.get(), requestHash), true);
        }
        try {
            IdempotencyRecordEntity created = idempotencyRepository.saveAndFlush(
                    new IdempotencyRecordEntity(UUID.randomUUID(), merchantId, idempotencyKey, requestHash, Instant.now())
            );
            return new ClaimResult(created, false);
        } catch (DataIntegrityViolationException ex) {
            // Lost race to concurrent thread: read in a fresh lookup
            throw new ConcurrentIdempotencyRaceException();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public IdempotencyRecordEntity getValidatedExisting(UUID merchantId, String idempotencyKey, String requestHash) {
        IdempotencyRecordEntity existing = idempotencyRepository
                .findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .orElseThrow(() -> new DomainException("IDEMPOTENCY_IN_PROGRESS",
                        "Payment with this Idempotency-Key is currently being processed", HttpStatus.CONFLICT));
        return validateHash(existing, requestHash);
    }

    private IdempotencyRecordEntity validateHash(IdempotencyRecordEntity record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new DomainException(
                    "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                    "Idempotency-Key was already used with a different payment payload",
                    HttpStatus.CONFLICT
            );
        }
        return record;
    }

    public static class ConcurrentIdempotencyRaceException extends RuntimeException {}
}
