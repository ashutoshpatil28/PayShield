package com.payshield.ledger;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import com.payshield.common.Money;
import com.payshield.common.PagedResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerBalanceRepository balanceRepository;
    private final JournalTransactionRepository txRepository;
    private final JournalLineRepository lineRepository;
    private final LedgerPostingReferenceRepository postingRefRepository;
    private final AuditService auditService;

    public LedgerService(
            LedgerAccountRepository accountRepository,
            LedgerBalanceRepository balanceRepository,
            JournalTransactionRepository txRepository,
            JournalLineRepository lineRepository,
            LedgerPostingReferenceRepository postingRefRepository,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.txRepository = txRepository;
        this.lineRepository = lineRepository;
        this.postingRefRepository = postingRefRepository;
        this.auditService = auditService;
    }

    public record PostingLineSpec(
            String accountCode,
            JournalLineEntity.EntryType entryType,
            long amountMinor
    ) {}

    public record JournalLineDto(
            UUID id,
            UUID ledgerAccountId,
            String accountCode,
            String accountName,
            JournalLineEntity.EntryType entryType,
            long amountMinor,
            String currency
    ) {}

    public record JournalTransactionDto(
            UUID id,
            String referenceCode,
            String description,
            String currency,
            UUID reversalOfTxId,
            String createdBy,
            Instant createdAt,
            List<JournalLineDto> lines
    ) {}

    public record AccountBalanceDto(
            UUID accountId,
            String accountCode,
            String accountName,
            LedgerAccountEntity.AccountType accountType,
            UUID merchantId,
            String currency,
            long balanceMinor,
            Instant updatedAt
    ) {}

    public record FinancialSummaryDto(
            long totalTransactions,
            long totalDebitsMinor,
            long totalCreditsMinor,
            boolean balanced,
            Map<String, Long> totalsByAccountType
    ) {}

    @Transactional
    public JournalTransactionDto postJournal(
            String sourceType,
            String sourceId,
            String postingType,
            String description,
            String currency,
            List<PostingLineSpec> lines,
            String actor,
            UUID reversalOfTxId
    ) {
        // 1. Check duplicate posting reference
        var existingRef = postingRefRepository.findBySourceTypeAndSourceIdAndPostingType(sourceType, sourceId, postingType);
        if (existingRef.isPresent()) {
            return getTransaction(existingRef.get().getJournalTxId());
        }

        // 2. Validate double-entry invariants
        if (lines == null || lines.size() < 2) {
            throw new DomainException("INVALID_JOURNAL_LINES", "A journal transaction requires at least 2 lines");
        }
        long totalDebits = 0L;
        long totalCredits = 0L;
        for (PostingLineSpec line : lines) {
            Money.positive(line.amountMinor(), currency);
            if (line.entryType() == JournalLineEntity.EntryType.DEBIT) {
                totalDebits = Math.addExact(totalDebits, line.amountMinor());
            } else {
                totalCredits = Math.addExact(totalCredits, line.amountMinor());
            }
        }
        if (totalDebits != totalCredits) {
            throw new DomainException("UNBALANCED_JOURNAL_TRANSACTION",
                    "Journal debits (" + totalDebits + ") must equal credits (" + totalCredits + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = Instant.now();
        UUID txId = UUID.randomUUID();
        String refCode = sourceType + ":" + sourceId + ":" + postingType;

        JournalTransactionEntity tx = new JournalTransactionEntity(
                txId, refCode, description, currency.toUpperCase(), reversalOfTxId, actor, now
        );
        txRepository.save(tx);

        try {
            postingRefRepository.saveAndFlush(new LedgerPostingReferenceEntity(
                    UUID.randomUUID(), sourceType, sourceId, postingType, txId, now
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_LEDGER_POSTING",
                    "Ledger posting already exists for " + refCode, HttpStatus.CONFLICT);
        }

        for (PostingLineSpec spec : lines) {
            LedgerAccountEntity account = accountRepository.findByAccountCode(spec.accountCode())
                    .orElseThrow(() -> new DomainException("LEDGER_ACCOUNT_NOT_FOUND",
                            "Unknown ledger account: " + spec.accountCode(), HttpStatus.BAD_REQUEST));
            if (!account.getCurrency().equalsIgnoreCase(currency)) {
                throw new DomainException("LEDGER_CURRENCY_MISMATCH",
                        "Account " + account.getAccountCode() + " currency " + account.getCurrency()
                                + " does not match " + currency, HttpStatus.BAD_REQUEST);
            }

            lineRepository.save(new JournalLineEntity(
                    UUID.randomUUID(), txId, account.getId(), spec.entryType(),
                    spec.amountMinor(), currency.toUpperCase(), now
            ));

            LedgerBalanceEntity balance = balanceRepository.findById(account.getId())
                    .orElse(new LedgerBalanceEntity(account.getId(), account.getCurrency(), 0L, now));
            long signedDelta = computeSignedDelta(account.getAccountType(), spec.entryType(), spec.amountMinor());
            balance.applyDelta(signedDelta);
            balanceRepository.save(balance);
        }

        return getTransaction(txId);
    }

    @Transactional
    public JournalTransactionDto reverseTransaction(UUID originalTxId, String reason, UUID actorId, String actorEmail, String roles, String ip) {
        JournalTransactionEntity original = txRepository.findById(originalTxId)
                .orElseThrow(() -> new DomainException("JOURNAL_TX_NOT_FOUND",
                        "Journal transaction not found", HttpStatus.NOT_FOUND));

        List<JournalLineEntity> origLines = lineRepository.findByJournalTxId(originalTxId);
        List<PostingLineSpec> reversalLines = new ArrayList<>();
        Map<UUID, LedgerAccountEntity> accountCache = new HashMap<>();

        for (JournalLineEntity line : origLines) {
            LedgerAccountEntity acc = accountCache.computeIfAbsent(line.getLedgerAccountId(),
                    id -> accountRepository.findById(id).orElseThrow());
            JournalLineEntity.EntryType flipped = line.getEntryType() == JournalLineEntity.EntryType.DEBIT
                    ? JournalLineEntity.EntryType.CREDIT
                    : JournalLineEntity.EntryType.DEBIT;
            reversalLines.add(new PostingLineSpec(acc.getAccountCode(), flipped, line.getAmountMinor()));
        }

        JournalTransactionDto reversal = postJournal(
                "REVERSAL",
                originalTxId.toString(),
                "REVERSAL_" + UUID.randomUUID().toString().substring(0, 8),
                "Reversal of " + original.getReferenceCode() + ": " + reason,
                original.getCurrency(),
                reversalLines,
                actorEmail,
                originalTxId
        );

        auditService.record(actorId, actorEmail, roles, "LEDGER_REVERSAL_POSTED",
                "JOURNAL_TRANSACTION", reversal.id().toString(), AuditOutcome.SUCCESS, ip,
                "{\"reversedTxId\":\"" + originalTxId + "\"}");
        return reversal;
    }

    @Transactional(readOnly = true)
    public List<AccountBalanceDto> listBalances() {
        List<LedgerAccountEntity> accounts = accountRepository.findAll();
        Map<UUID, LedgerBalanceEntity> balances = new HashMap<>();
        balanceRepository.findAll().forEach(b -> balances.put(b.getLedgerAccountId(), b));

        return accounts.stream()
                .sorted(Comparator.comparing(LedgerAccountEntity::getAccountCode))
                .map(a -> {
                    LedgerBalanceEntity bal = balances.get(a.getId());
                    return new AccountBalanceDto(
                            a.getId(), a.getAccountCode(), a.getAccountName(),
                            a.getAccountType(), a.getMerchantId(), a.getCurrency(),
                            bal != null ? bal.getBalanceMinor() : 0L,
                            bal != null ? bal.getUpdatedAt() : a.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialSummaryDto getFinancialSummary() {
        long debits = 0L;
        long credits = 0L;
        for (JournalLineEntity l : lineRepository.findAll()) {
            if (l.getEntryType() == JournalLineEntity.EntryType.DEBIT) {
                debits = Math.addExact(debits, l.getAmountMinor());
            } else {
                credits = Math.addExact(credits, l.getAmountMinor());
            }
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (AccountBalanceDto b : listBalances()) {
            byType.merge(b.accountType().name(), b.balanceMinor(), Math::addExact);
        }
        return new FinancialSummaryDto(txRepository.count(), debits, credits, debits == credits, byType);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JournalTransactionDto> listTransactions(int page, int size) {
        var pageData = txRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PagedResponse.from(pageData.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public JournalTransactionDto getTransaction(UUID txId) {
        JournalTransactionEntity tx = txRepository.findById(txId)
                .orElseThrow(() -> new DomainException("JOURNAL_TX_NOT_FOUND",
                        "Journal transaction not found", HttpStatus.NOT_FOUND));
        return toDto(tx);
    }

    private JournalTransactionDto toDto(JournalTransactionEntity tx) {
        List<JournalLineEntity> lines = lineRepository.findByJournalTxId(tx.getId());
        Map<UUID, LedgerAccountEntity> accounts = new HashMap<>();
        List<JournalLineDto> lineDtos = lines.stream().map(l -> {
            LedgerAccountEntity acc = accounts.computeIfAbsent(l.getLedgerAccountId(),
                    id -> accountRepository.findById(id).orElseThrow());
            return new JournalLineDto(
                    l.getId(), l.getLedgerAccountId(), acc.getAccountCode(), acc.getAccountName(),
                    l.getEntryType(), l.getAmountMinor(), l.getCurrency()
            );
        }).toList();
        return new JournalTransactionDto(
                tx.getId(), tx.getReferenceCode(), tx.getDescription(), tx.getCurrency(),
                tx.getReversalOfTxId(), tx.getCreatedBy(), tx.getCreatedAt(), lineDtos
        );
    }

    private static long computeSignedDelta(LedgerAccountEntity.AccountType type,
                                           JournalLineEntity.EntryType entry,
                                           long amountMinor) {
        // Normal debit-balance accounts: ASSET, EXPENSE
        // Normal credit-balance accounts: LIABILITY, EQUITY, REVENUE
        boolean debitNormal = (type == LedgerAccountEntity.AccountType.ASSET
                || type == LedgerAccountEntity.AccountType.EXPENSE);
        boolean isDebit = (entry == JournalLineEntity.EntryType.DEBIT);
        return (debitNormal == isDebit) ? amountMinor : -amountMinor;
    }
}
