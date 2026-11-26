import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Activity, AlertTriangle, ArrowLeftRight, CheckCircle2, Clock, FileWarning, Send, XCircle } from 'lucide-react';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';

interface OverviewMetrics {
  totalPayments: number;
  succeededPayments: number;
  failedPayments: number;
  unknownPayments: number;
  pendingPayoutApprovals: number;
  openReconciliationCases: number;
  openRiskAlerts: number;
  outboxPendingCount: number;
  outboxFailedCount: number;
  providers: Array<{
    providerCode: string;
    displayName: string;
    enabled: boolean;
    failureMode: string;
    circuitBreakerState: string;
    successRatePercent: number;
    averageLatencyMs: number;
  }>;
}

export function OverviewPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['ops-overview'],
    queryFn: () => apiFetch<OverviewMetrics>('/api/v1/ops/overview'),
    refetchInterval: 10000,
  });

  if (isLoading) {
    return <div className="p-8 text-sm text-slate-500">Loading operational metrics...</div>;
  }
  if (error || !data) {
    return <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-500 text-sm">Failed to load overview metrics.</div>;
  }

  const chartData = [
    { name: 'Succeeded', count: data.succeededPayments },
    { name: 'Unknown', count: data.unknownPayments },
    { name: 'Failed', count: data.failedPayments },
  ];

  const kpis = [
    { label: 'Total Payments', value: data.totalPayments, icon: ArrowLeftRight, accent: 'text-slate-600 dark:text-slate-300' },
    { label: 'Succeeded', value: data.succeededPayments, icon: CheckCircle2, accent: 'text-emerald-500' },
    { label: 'Unknown (Timeout)', value: data.unknownPayments, icon: Clock, accent: 'text-amber-500' },
    { label: 'Failed', value: data.failedPayments, icon: XCircle, accent: 'text-rose-500' },
    { label: 'Pending Payouts', value: data.pendingPayoutApprovals, icon: Send, accent: 'text-sky-500' },
    { label: 'Recon Exceptions', value: data.openReconciliationCases, icon: FileWarning, accent: 'text-amber-500' },
    { label: 'Open Risk Alerts', value: data.openRiskAlerts, icon: AlertTriangle, accent: 'text-rose-500' },
    { label: 'Outbox Pending', value: data.outboxPendingCount, icon: Activity, accent: 'text-indigo-500' },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Operations Overview</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Real-time metrics derived directly from PayShield PostgreSQL state and simulated provider gateways
        </p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map((k) => {
          const Icon = k.icon;
          return (
            <div key={k.label} className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
              <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 mb-2">
                <span>{k.label}</span>
                <Icon className={`w-4 h-4 ${k.accent}`} />
              </div>
              <div className="text-2xl font-semibold tabular-nums">{k.value}</div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
          <h2 className="text-sm font-semibold mb-4">Payment Outcome Distribution</h2>
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.2} />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="count" fill="#10b981" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
          <h2 className="text-sm font-semibold mb-4">Simulated Provider Availability</h2>
          <div className="space-y-3">
            {data.providers.map((p) => (
              <div key={p.providerCode} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 flex items-center justify-between gap-3">
                <div>
                  <div className="font-medium text-sm">{p.displayName}</div>
                  <div className="text-xs text-slate-500 font-mono">
                    {p.providerCode} • Mode: {p.failureMode} • Avg {p.averageLatencyMs}ms
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono tabular-nums">{p.successRatePercent}%</span>
                  <StatusBadge value={p.circuitBreakerState} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
