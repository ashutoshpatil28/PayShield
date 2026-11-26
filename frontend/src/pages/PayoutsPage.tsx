import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Play, Plus, Power, X } from 'lucide-react';
import { apiFetch, ApiError } from '../lib/api';
import { formatMinorMoney, formatUtcTimestamp } from '../lib/format';
import { StatusBadge } from '../components/StatusBadge';
import { useAuth } from '../lib/auth';

interface Payout {
  id: string;
  merchantId: string;
  beneficiaryId: string;
  beneficiaryName: string;
  amountMinor: number;
  currency: string;
  status: string;
  purpose: string;
  riskScore: number;
  createdAt: string;
}

interface Beneficiary {
  id: string;
  merchantId: string;
  beneficiaryName: string;
  accountNumberMasked: string;
  allowlisted: boolean;
}

export function PayoutsPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [beneficiaryId, setBeneficiaryId] = useState('');
  const [amountMinor, setAmountMinor] = useState('150000');
  const [purpose, setPurpose] = useState('Vendor settlement batch');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { data: payouts } = useQuery({
    queryKey: ['payouts'],
    queryFn: () => apiFetch<{ items: Payout[] }>('/api/v1/payouts?size=50'),
  });

  const { data: killSwitch } = useQuery({
    queryKey: ['kill-switch'],
    queryFn: () => apiFetch<{ enabled: boolean; reason: string }>('/api/v1/payouts/kill-switch'),
  });

  const { data: beneficiaries } = useQuery({
    queryKey: ['beneficiaries'],
    queryFn: () => apiFetch<Beneficiary[]>('/api/v1/payouts/beneficiaries'),
  });

  const createMutation = useMutation({
    mutationFn: () => {
      const selected = beneficiaries?.find((b) => b.id === beneficiaryId) || beneficiaries?.[0];
      return apiFetch('/api/v1/payouts', {
        method: 'POST',
        body: JSON.stringify({
          merchantId: selected?.merchantId,
          beneficiaryId: selected?.id,
          amountMinor: Number(amountMinor),
          currency: 'INR',
          purpose,
        }),
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payouts'] });
      setShowCreate(false);
      setErrorMsg(null);
    },
    onError: (err) => setErrorMsg(err instanceof ApiError ? err.message : 'Failed to request payout'),
  });

  const decideMutation = useMutation({
    mutationFn: ({ id, approved }: { id: string; approved: boolean }) =>
      apiFetch(`/api/v1/payouts/${id}/decision`, {
        method: 'POST',
        body: JSON.stringify({ approved, comments: approved ? 'Approved via console' : 'Rejected via console' }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payouts'] });
      setErrorMsg(null);
    },
    onError: (err) => setErrorMsg(err instanceof ApiError ? err.message : 'Decision rejected'),
  });

  const executeMutation = useMutation({
    mutationFn: (id: string) => apiFetch(`/api/v1/payouts/${id}/execute`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payouts'] });
      qc.invalidateQueries({ queryKey: ['ledger-balances'] });
      setErrorMsg(null);
    },
    onError: (err) => setErrorMsg(err instanceof ApiError ? err.message : 'Execution rejected'),
  });

  const toggleKillSwitch = useMutation({
    mutationFn: (enabled: boolean) =>
      apiFetch('/api/v1/payouts/kill-switch', {
        method: 'PUT',
        body: JSON.stringify({ enabled, reason: enabled ? 'Emergency halt from console' : 'Resumed from console' }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['kill-switch'] }),
  });

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Payout Authorization & Queue</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Maker-checker separation of duties, allowlisted beneficiaries, and emergency kill switch
          </p>
        </div>
        <div className="flex items-center gap-2">
          {hasRole('PLATFORM_ADMIN') && killSwitch && (
            <button
              type="button"
              onClick={() => toggleKillSwitch.mutate(!killSwitch.enabled)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium border ${
                killSwitch.enabled
                  ? 'bg-rose-600 text-white border-rose-500'
                  : 'border-slate-300 dark:border-navy-700 hover:bg-slate-100 dark:hover:bg-navy-800'
              }`}
            >
              <Power className="w-3.5 h-3.5" />
              {killSwitch.enabled ? 'Kill Switch: ACTIVE (Click to Resume)' : 'Activate Kill Switch'}
            </button>
          )}
          {hasRole('PLATFORM_ADMIN', 'MERCHANT_ADMIN', 'PAYOUT_MAKER') && (
            <button
              type="button"
              onClick={() => setShowCreate(true)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
            >
              <Plus className="w-4 h-4" />
              Request Payout
            </button>
          )}
        </div>
      </div>

      {errorMsg && (
        <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm">{errorMsg}</div>
      )}

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-4 py-3">Beneficiary</th>
              <th className="px-4 py-3">Purpose</th>
              <th className="px-4 py-3">Amount</th>
              <th className="px-4 py-3">Risk</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Created</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
            {payouts?.items.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-slate-500">No payout requests yet.</td>
              </tr>
            )}
            {payouts?.items.map((p) => (
              <tr key={p.id}>
                <td className="px-4 py-3 font-medium">{p.beneficiaryName}</td>
                <td className="px-4 py-3 text-xs text-slate-500">{p.purpose}</td>
                <td className="px-4 py-3 tabular-nums font-medium">{formatMinorMoney(p.amountMinor, p.currency)}</td>
                <td className="px-4 py-3 tabular-nums text-xs">{p.riskScore}/100</td>
                <td className="px-4 py-3"><StatusBadge value={p.status} /></td>
                <td className="px-4 py-3 text-xs text-slate-500">{formatUtcTimestamp(p.createdAt)}</td>
                <td className="px-4 py-3 text-right space-x-1.5">
                  {p.status === 'PENDING_MAKER_CHECKER' && hasRole('PLATFORM_ADMIN', 'PAYOUT_CHECKER') && (
                    <>
                      <button
                        type="button"
                        onClick={() => decideMutation.mutate({ id: p.id, approved: true })}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs"
                      >
                        <Check className="w-3 h-3" /> Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => decideMutation.mutate({ id: p.id, approved: false })}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-rose-600 hover:bg-rose-500 text-white text-xs"
                      >
                        <X className="w-3 h-3" /> Reject
                      </button>
                    </>
                  )}
                  {p.status === 'APPROVED' && hasRole('PLATFORM_ADMIN', 'OPERATIONS_ANALYST', 'PAYOUT_CHECKER') && (
                    <button
                      type="button"
                      onClick={() => executeMutation.mutate(p.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-sky-600 hover:bg-sky-500 text-white text-xs"
                    >
                      <Play className="w-3 h-3" /> Execute
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-6 space-y-4">
            <h2 className="text-lg font-semibold">Request Payout</h2>
            <div className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium mb-1">Beneficiary</label>
                <select
                  value={beneficiaryId || beneficiaries?.[0]?.id || ''}
                  onChange={(e) => setBeneficiaryId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                >
                  {beneficiaries?.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.beneficiaryName} ({b.accountNumberMasked}) {b.allowlisted ? '✓' : '— NOT ALLOWLISTED'}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Amount (INR Paise)</label>
                <input
                  type="number"
                  min={1}
                  value={amountMinor}
                  onChange={(e) => setAmountMinor(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Purpose</label>
                <input
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setShowCreate(false)} className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-sm">
                Cancel
              </button>
              <button
                type="button"
                onClick={() => createMutation.mutate()}
                className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
              >
                Submit Request
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
