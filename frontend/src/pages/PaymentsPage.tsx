import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import { apiFetch, ApiError } from '../lib/api';
import { formatMinorMoney, formatUtcTimestamp } from '../lib/format';
import { StatusBadge } from '../components/StatusBadge';
import { useAuth } from '../lib/auth';

interface Payment {
  id: string;
  merchantId: string;
  merchantOrderRef: string;
  amountMinor: number;
  currency: string;
  status: string;
  selectedProvider: string;
  providerReference: string | null;
  createdAt: string;
}

interface PagedPayments {
  items: Payment[];
  page: number;
  totalElements: number;
  totalPages: number;
}

interface Merchant {
  id: string;
  merchantCode: string;
  displayName: string;
}

export function PaymentsPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);

  const [merchantId, setMerchantId] = useState('22222222-2222-2222-2222-222222222201');
  const [orderRef, setOrderRef] = useState(() => `ORD-${Math.floor(10000 + Math.random() * 90000)}`);
  const [amountMinor, setAmountMinor] = useState('125000');
  const [provider, setProvider] = useState('PROVIDER_ALPHA');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: merchants } = useQuery({
    queryKey: ['merchants'],
    queryFn: () => apiFetch<Merchant[]>('/api/v1/merchants'),
  });

  const { data, isLoading } = useQuery({
    queryKey: ['payments', page, statusFilter, search],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '20' });
      if (statusFilter) params.set('status', statusFilter);
      if (search.trim()) params.set('search', search.trim());
      return apiFetch<PagedPayments>(`/api/v1/payments?${params.toString()}`);
    },
  });

  const createMutation = useMutation({
    mutationFn: () =>
      apiFetch<Payment>('/api/v1/payments', {
        method: 'POST',
        headers: { 'Idempotency-Key': `ui-${crypto.randomUUID()}` },
        body: JSON.stringify({
          merchantId: merchants?.[0]?.id || merchantId,
          merchantOrderRef: orderRef,
          amountMinor: Number(amountMinor),
          currency: 'INR',
          preferredProvider: provider,
        }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['ops-overview'] });
      setShowModal(false);
      setOrderRef(`ORD-${Math.floor(10000 + Math.random() * 90000)}`);
      setFormError(null);
    },
    onError: (err) => {
      setFormError(err instanceof ApiError ? err.message : 'Failed to create payment');
    },
  });

  const canCreate = hasRole('PLATFORM_ADMIN', 'MERCHANT_ADMIN', 'OPERATIONS_ANALYST');

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Payments</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Idempotent payment orchestration, provider routing, and lifecycle tracking
          </p>
        </div>
        {canCreate && (
          <button
            type="button"
            onClick={() => setShowModal(true)}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
          >
            <Plus className="w-4 h-4" />
            Initiate Payment
          </button>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search order ref or provider ref..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="w-full pl-9 pr-3 py-2 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm"
        >
          <option value="">All Statuses</option>
          <option value="SUCCEEDED">SUCCEEDED</option>
          <option value="UNKNOWN">UNKNOWN</option>
          <option value="FAILED">FAILED</option>
          <option value="PROCESSING">PROCESSING</option>
        </select>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-4 py-3">Order Ref</th>
              <th className="px-4 py-3">Amount</th>
              <th className="px-4 py-3">Provider</th>
              <th className="px-4 py-3">Provider Ref</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Created</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
            {isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-500">Loading payments...</td>
              </tr>
            )}
            {!isLoading && data?.items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-500">No payments recorded yet.</td>
              </tr>
            )}
            {data?.items.map((p) => (
              <tr key={p.id} className="hover:bg-slate-50 dark:hover:bg-navy-800/40">
                <td className="px-4 py-3 font-medium">
                  <Link to={`/payments/${p.id}`} className="text-emerald-600 dark:text-emerald-400 hover:underline font-mono">
                    {p.merchantOrderRef}
                  </Link>
                </td>
                <td className="px-4 py-3 tabular-nums font-medium">{formatMinorMoney(p.amountMinor, p.currency)}</td>
                <td className="px-4 py-3 font-mono text-xs">{p.selectedProvider}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-500">{p.providerReference || '—'}</td>
                <td className="px-4 py-3"><StatusBadge value={p.status} /></td>
                <td className="px-4 py-3 text-xs text-slate-500">{formatUtcTimestamp(p.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-6 space-y-4">
            <h2 className="text-lg font-semibold">Initiate Simulated Payment</h2>
            {formError && <div className="p-2.5 rounded bg-rose-500/10 text-rose-400 text-xs">{formError}</div>}
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium mb-1">Merchant</label>
                <select
                  value={merchantId}
                  onChange={(e) => setMerchantId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-sm"
                >
                  {merchants?.map((m) => (
                    <option key={m.id} value={m.id}>{m.displayName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Merchant Order Ref</label>
                <input
                  value={orderRef}
                  onChange={(e) => setOrderRef(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Amount (INR Paise, e.g. 10050 = ₹100.50)</label>
                <input
                  type="number"
                  min={1}
                  value={amountMinor}
                  onChange={(e) => setAmountMinor(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Provider</label>
                <select
                  value={provider}
                  onChange={(e) => setProvider(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-sm"
                >
                  <option value="PROVIDER_ALPHA">PROVIDER_ALPHA</option>
                  <option value="PROVIDER_BETA">PROVIDER_BETA</option>
                  <option value="PROVIDER_GAMMA">PROVIDER_GAMMA</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowModal(false)}
                className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
              >
                {createMutation.isPending ? 'Processing...' : 'Submit Payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
