import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { formatUtcTimestamp } from '../lib/format';

interface HealthData {
  status: string;
  database: string;
  uptimeSeconds: number;
  timestamp: string;
}

interface OverviewData {
  outboxPendingCount: number;
  outboxFailedCount: number;
  providers: Array<{
    providerCode: string;
    displayName: string;
    circuitBreakerState: string;
    averageLatencyMs: number;
    errorRatePercent: number;
  }>;
}

export function SystemHealthPage() {
  const { data: health } = useQuery({
    queryKey: ['ops-health'],
    queryFn: () => apiFetch<HealthData>('/api/v1/ops/health'),
    refetchInterval: 5000,
  });

  const { data: overview } = useQuery({
    queryKey: ['ops-overview'],
    queryFn: () => apiFetch<OverviewData>('/api/v1/ops/overview'),
    refetchInterval: 5000,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">System Health & Outbox Queue</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Live JVM, PostgreSQL, transactional outbox, and provider gateway telemetry
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-2">Application</div>
          <StatusBadge value={health?.status || 'CHECKING'} />
        </div>
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-2">Database</div>
          <StatusBadge value={health?.database || 'CHECKING'} />
        </div>
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-1">Uptime</div>
          <div className="text-xl font-semibold tabular-nums">{health?.uptimeSeconds ?? 0}s</div>
        </div>
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
          <div className="text-xs text-slate-500 mb-1">Outbox Pending / Failed</div>
          <div className="text-xl font-semibold tabular-nums">
            {overview?.outboxPendingCount ?? 0} / {overview?.outboxFailedCount ?? 0}
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold">Provider Circuit Breakers & Latency</h2>
          <span className="text-xs text-slate-500">Last checked: {formatUtcTimestamp(health?.timestamp)}</span>
        </div>
        <div className="space-y-2.5">
          {overview?.providers.map((p) => (
            <div key={p.providerCode} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 flex items-center justify-between text-xs">
              <div>
                <div className="font-semibold">{p.displayName}</div>
                <div className="font-mono text-slate-500">{p.providerCode}</div>
              </div>
              <div className="flex items-center gap-4">
                <span className="tabular-nums">Latency: {p.averageLatencyMs}ms</span>
                <span className="tabular-nums">Error: {p.errorRatePercent}%</span>
                <StatusBadge value={p.circuitBreakerState} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
