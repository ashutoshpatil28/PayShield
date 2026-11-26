import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { apiFetch } from '../lib/api';
import { formatMinorMoney, formatUtcTimestamp } from '../lib/format';
import { StatusBadge } from '../components/StatusBadge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { PaginationControls } from '../components/PaginationControls';
import { useAuth } from '../lib/auth';

interface AccountBalance {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  currency: string;
  balanceMinor: number;
}

interface FinancialSummary {
  totalTransactions: number;
  totalDebitsMinor: number;
  totalCreditsMinor: number;
  balanced: boolean;
}

interface JournalTx {
  id: string;
  referenceCode: string;
  description: string;
  currency: string;
  reversalOfTxId: string | null;
  createdBy: string;
  createdAt: string;
  lines: Array<{
    id: string;
    accountCode: string;
    accountName: string;
    entryType: 'DEBIT' | 'CREDIT';
    amountMinor: number;
    currency: string;
  }>;
}

interface PagedJournal {
  items: JournalTx[];
  page: number;
  totalElements: number;
  totalPages: number;
}

export function LedgerPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [reverseTxId, setReverseTxId] = useState<string | null>(null);

  const { data: summary } = useQuery({
    queryKey: ['ledger-summary'],
    queryFn: () => apiFetch<FinancialSummary>('/api/v1/ledger/summary'),
  });

  const { data: balances } = useQuery({
    queryKey: ['ledger-balances'],
    queryFn: () => apiFetch<AccountBalance[]>('/api/v1/ledger/balances'),
  });

  const { data: txs } = useQuery({
    queryKey: ['ledger-txs', page],
    queryFn: () => apiFetch<PagedJournal>(`/api/v1/ledger/transactions?page=${page}&size=15`),
  });

  const reverseMutation = useMutation({
    mutationFn: (txId: string) =>
      apiFetch(`/api/v1/ledger/transactions/${txId}/reverse`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'Manual reversal via Ledger Explorer' }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['ledger-balances'] });
      qc.invalidateQueries({ queryKey: ['ledger-txs'] });
      qc.invalidateQueries({ queryKey: ['ledger-summary'] });
      setReverseTxId(null);
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Double-Entry Financial Ledger</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Immutable journal transactions (SUM(DEBIT) == SUM(CREDIT)) and real-time account balances
          </p>
        </div>
        {summary && (
          <div className="flex items-center gap-3 px-4 py-2 rounded-xl bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-xs">
            <span>Debits: <strong className="tabular-nums">{formatMinorMoney(summary.totalDebitsMinor)}</strong></span>
            <span>Credits: <strong className="tabular-nums">{formatMinorMoney(summary.totalCreditsMinor)}</strong></span>
            <StatusBadge value={summary.balanced ? 'SUCCEEDED' : 'FAILED'} />
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {balances?.map((b) => (
          <div key={b.accountId} className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
            <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
              <span>{b.accountType}</span>
              <span className="font-mono">{b.currency}</span>
            </div>
            <div className="font-medium text-sm">{b.accountName}</div>
            <div className="text-xl font-semibold tabular-nums mt-2">{formatMinorMoney(b.balanceMinor, b.currency)}</div>
            <div className="text-[11px] font-mono text-slate-500 truncate mt-1">{b.accountCode}</div>
          </div>
        ))}
      </div>

      <div className="space-y-3">
        <h2 className="text-sm font-semibold">Journal Transactions</h2>
        {txs?.items.length === 0 && (
          <div className="p-6 rounded-xl bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm text-slate-500">
            No journal entries posted yet. Initiate a payment or execute a payout to see balanced postings.
          </div>
        )}
        {txs?.items.map((tx) => (
          <div key={tx.id} className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <div className="font-mono text-xs font-semibold">{tx.referenceCode}</div>
                <div className="text-xs text-slate-500">
                  {tx.description} • {tx.createdBy} • {formatUtcTimestamp(tx.createdAt)}
                </div>
              </div>
              {hasRole('PLATFORM_ADMIN') && !tx.reversalOfTxId && (
                <button
                  type="button"
                  onClick={() => setReverseTxId(tx.id)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded border border-slate-300 dark:border-navy-700 text-xs hover:bg-slate-100 dark:hover:bg-navy-800"
                >
                  <RotateCcw className="w-3 h-3" /> Post Reversal
                </button>
              )}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
              {tx.lines.map((l) => (
                <div key={l.id} className="p-2 rounded bg-slate-50 dark:bg-navy-950 flex items-center justify-between">
                  <div>
                    <span className={`font-semibold mr-2 ${l.entryType === 'DEBIT' ? 'text-sky-500' : 'text-emerald-500'}`}>
                      {l.entryType}
                    </span>
                    <span className="font-mono">{l.accountName}</span>
                  </div>
                  <span className="tabular-nums font-medium">{formatMinorMoney(l.amountMinor, l.currency)}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
        {txs && txs.totalElements > 0 && (
          <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl">
            <PaginationControls
              page={txs.page}
              totalPages={txs.totalPages}
              totalElements={txs.totalElements}
              onPageChange={setPage}
            />
          </div>
        )}
      </div>

      <ConfirmDialog
        open={!!reverseTxId}
        title="Post Reversal Journal Transaction?"
        description="Historical journal entries are immutable. This will post a new balanced reversal transaction offsetting the original debits and credits."
        confirmLabel="Post Reversal"
        destructive
        loading={reverseMutation.isPending}
        onConfirm={() => reverseTxId && reverseMutation.mutate(reverseTxId)}
        onCancel={() => setReverseTxId(null)}
      />
    </div>
  );
}
