import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, RefreshCw } from 'lucide-react';
import { apiFetch } from '../lib/api';
import { formatMinorMoney, formatUtcTimestamp } from '../lib/format';
import { StatusBadge } from '../components/StatusBadge';

interface PaymentDetail {
  payment: {
    id: string;
    merchantOrderRef: string;
    amountMinor: number;
    currency: string;
    status: string;
    selectedProvider: string;
    providerReference: string | null;
    idempotencyKey: string;
    failureReason: string | null;
    createdAt: string;
  };
  attempts: Array<{
    id: string;
    attemptNumber: number;
    providerCode: string;
    providerRequestId: string;
    providerReference: string | null;
    outcome: string;
    latencyMs: number;
    errorMessage: string | null;
    createdAt: string;
  }>;
  events: Array<{
    id: string;
    fromStatus: string | null;
    toStatus: string;
    eventType: string;
    actor: string;
    correlationId: string | null;
    createdAt: string;
  }>;
}

export function PaymentDetailPage() {
  const { paymentId } = useParams();
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['payment-detail', paymentId],
    queryFn: () => apiFetch<PaymentDetail>(`/api/v1/payments/${paymentId}`),
    enabled: !!paymentId,
  });

  const verifyMutation = useMutation({
    mutationFn: () => apiFetch(`/api/v1/payments/${paymentId}/verify`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payment-detail', paymentId] });
      qc.invalidateQueries({ queryKey: ['payments'] });
    },
  });

  if (isLoading || !data) {
    return <div className="p-8 text-sm text-slate-500">Loading payment details...</div>;
  }

  const p = data.payment;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/payments" className="p-2 rounded-lg border border-slate-200 dark:border-navy-700 hover:bg-slate-100 dark:hover:bg-navy-800">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-xl font-semibold font-mono">{p.merchantOrderRef}</h1>
              <StatusBadge value={p.status} />
            </div>
            <p className="text-xs text-slate-500 font-mono">{p.id}</p>
          </div>
        </div>

        {(p.status === 'UNKNOWN' || p.status === 'PROCESSING') && (
          <button
            type="button"
            onClick={() => verifyMutation.mutate()}
            disabled={verifyMutation.isPending}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-500 text-white text-sm font-medium"
          >
            <RefreshCw className="w-4 h-4" />
            {verifyMutation.isPending ? 'Verifying...' : 'Verify Status with Provider'}
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-1">Amount</div>
          <div className="text-xl font-semibold tabular-nums">{formatMinorMoney(p.amountMinor, p.currency)}</div>
        </div>
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-1">Provider & Reference</div>
          <div className="text-sm font-mono">{p.selectedProvider}</div>
          <div className="text-xs font-mono text-slate-500">{p.providerReference || 'Pending / None'}</div>
        </div>
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-1">Idempotency Key</div>
          <div className="text-xs font-mono break-all">{p.idempotencyKey}</div>
        </div>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold mb-3">Provider Attempts</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs uppercase text-slate-500 border-b border-slate-200 dark:border-navy-700">
              <tr>
                <th className="py-2 pr-4">#</th>
                <th className="py-2 pr-4">Provider</th>
                <th className="py-2 pr-4">Request ID</th>
                <th className="py-2 pr-4">Outcome</th>
                <th className="py-2 pr-4">Latency</th>
                <th className="py-2">Notes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
              {data.attempts.map((a) => (
                <tr key={a.id}>
                  <td className="py-2.5 pr-4">{a.attemptNumber}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs">{a.providerCode}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs">{a.providerRequestId}</td>
                  <td className="py-2.5 pr-4"><StatusBadge value={a.outcome} /></td>
                  <td className="py-2.5 pr-4 tabular-nums">{a.latencyMs}ms</td>
                  <td className="py-2.5 text-xs text-slate-500">{a.errorMessage || 'OK'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold mb-3">State Transition Event Timeline</h2>
        <div className="space-y-2.5">
          {data.events.map((ev) => (
            <div key={ev.id} className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-200 dark:border-navy-700 text-xs">
              <div className="flex items-center gap-2">
                <span className="font-mono font-semibold">{ev.eventType}</span>
                <span className="text-slate-500">
                  ({ev.fromStatus || 'NONE'} → {ev.toStatus})
                </span>
              </div>
              <div className="text-slate-500">
                {ev.actor} • {formatUtcTimestamp(ev.createdAt)}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
