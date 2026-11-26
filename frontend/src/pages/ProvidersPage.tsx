import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Sliders } from 'lucide-react';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { formatUtcTimestamp } from '../lib/format';

interface ProviderStatus {
  providerCode: string;
  displayName: string;
  enabled: boolean;
  failureMode: string;
  latencyMs: number;
  failureRatePercent: number;
  timeoutRatePercent: number;
  circuitBreakerState: string;
  totalRequests: number;
  successCount: number;
  failureCount: number;
  successRatePercent: number;
  errorRatePercent: number;
  averageLatencyMs: number;
  lastSuccessAt: string | null;
}

const FAILURE_MODES = [
  'NORMAL_SUCCESS',
  'DECLINE',
  'DELAYED_RESPONSE',
  'TIMEOUT_AFTER_PROCESSING',
  'TIMEOUT_BEFORE_PROCESSING',
  'UNAVAILABLE',
  'DUPLICATE_WEBHOOK',
  'DELAYED_WEBHOOK',
  'INVALID_RESPONSE',
  'INTERMITTENT_ERRORS',
];

export function ProvidersPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<ProviderStatus | null>(null);

  const { data: providers, isLoading } = useQuery({
    queryKey: ['providers'],
    queryFn: () => apiFetch<ProviderStatus[]>('/api/v1/providers'),
    refetchInterval: 8000,
  });

  const updateMutation = useMutation({
    mutationFn: (cfg: ProviderStatus) =>
      apiFetch(`/api/v1/providers/${cfg.providerCode}/config`, {
        method: 'PUT',
        body: JSON.stringify({
          enabled: cfg.enabled,
          failureMode: cfg.failureMode,
          latencyMs: cfg.latencyMs,
          failureRatePercent: cfg.failureRatePercent,
          timeoutRatePercent: cfg.timeoutRatePercent,
        }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['providers'] });
      setEditing(null);
    },
  });

  const resetCbMutation = useMutation({
    mutationFn: (code: string) =>
      apiFetch(`/api/v1/providers/${code}/circuit-breaker/reset`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['providers'] }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Simulated Payment Providers</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Configure synthetic failure injection, timeouts, and Resilience4j circuit breakers
        </p>
      </div>

      {isLoading && <div className="text-sm text-slate-500">Loading provider simulators...</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {providers?.map((p) => (
          <div key={p.providerCode} className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5 space-y-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h2 className="font-semibold text-sm">{p.displayName}</h2>
                <div className="text-xs font-mono text-slate-500">{p.providerCode}</div>
              </div>
              <StatusBadge value={p.circuitBreakerState} />
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg bg-slate-50 dark:bg-navy-950">
                <div className="text-slate-500">Success / Error</div>
                <div className="font-semibold tabular-nums mt-0.5">
                  {p.successRatePercent}% / {p.errorRatePercent}%
                </div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-50 dark:bg-navy-950">
                <div className="text-slate-500">Avg Latency</div>
                <div className="font-semibold tabular-nums mt-0.5">{p.averageLatencyMs} ms</div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-50 dark:bg-navy-950 col-span-2">
                <div className="text-slate-500">Current Failure Mode</div>
                <div className="font-mono font-medium mt-0.5">{p.failureMode}</div>
              </div>
            </div>

            <div className="text-[11px] text-slate-500">
              Requests: {p.totalRequests} • Last success: {formatUtcTimestamp(p.lastSuccessAt)}
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                onClick={() => setEditing({ ...p })}
                className="flex-1 inline-flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-xs font-medium hover:bg-slate-100 dark:hover:bg-navy-800"
              >
                <Sliders className="w-3.5 h-3.5" />
                Configure Simulator
              </button>
              <button
                type="button"
                onClick={() => resetCbMutation.mutate(p.providerCode)}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-xs hover:bg-slate-100 dark:hover:bg-navy-800"
                title="Reset Circuit Breaker"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-6 space-y-4">
            <h2 className="text-lg font-semibold">Configure {editing.providerCode}</h2>
            <div className="space-y-3 text-sm">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={editing.enabled}
                  onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
                />
                <span>Provider Enabled</span>
              </label>
              <div>
                <label className="block text-xs font-medium mb-1">Failure Mode</label>
                <select
                  value={editing.failureMode}
                  onChange={(e) => setEditing({ ...editing, failureMode: e.target.value })}
                  className="w-full px-3 py-2 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 font-mono text-xs"
                >
                  {FAILURE_MODES.map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="block text-xs mb-1">Latency (ms)</label>
                  <input
                    type="number"
                    min={0}
                    max={2000}
                    value={editing.latencyMs}
                    onChange={(e) => setEditing({ ...editing, latencyMs: Number(e.target.value) })}
                    className="w-full px-2 py-1.5 rounded bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs mb-1">Fail %</label>
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={editing.failureRatePercent}
                    onChange={(e) => setEditing({ ...editing, failureRatePercent: Number(e.target.value) })}
                    className="w-full px-2 py-1.5 rounded bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs mb-1">Timeout %</label>
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={editing.timeoutRatePercent}
                    onChange={(e) => setEditing({ ...editing, timeoutRatePercent: Number(e.target.value) })}
                    className="w-full px-2 py-1.5 rounded bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-xs"
                  />
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setEditing(null)} className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-navy-700 text-sm">
                Cancel
              </button>
              <button
                type="button"
                onClick={() => updateMutation.mutate(editing)}
                className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
              >
                Save Config
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
