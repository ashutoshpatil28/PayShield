import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { PaginationControls } from '../components/PaginationControls';
import { formatUtcTimestamp } from '../lib/format';
import { useAuth } from '../lib/auth';

interface RiskAlert {
  id: string;
  ruleCode: string;
  entityType: string;
  entityId: string;
  severity: string;
  riskScore: number;
  status: string;
  explanation: string;
  createdAt: string;
}

interface PagedAlerts {
  items: RiskAlert[];
  page: number;
  totalElements: number;
  totalPages: number;
}

interface RiskRule {
  ruleCode: string;
  name: string;
  description: string;
  severity: string;
  scoreWeight: number;
  enabled: boolean;
  thresholdValue: number;
}

export function RiskPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);

  const { data: alerts } = useQuery({
    queryKey: ['risk-alerts', page, statusFilter],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (statusFilter) params.set('status', statusFilter);
      return apiFetch<PagedAlerts>(`/api/v1/risk/alerts?${params.toString()}`);
    },
  });

  const { data: rules } = useQuery({
    queryKey: ['risk-rules'],
    queryFn: () => apiFetch<RiskRule[]>('/api/v1/risk/rules'),
  });

  const updateAlert = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiFetch(`/api/v1/risk/alerts/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status, analystNotes: `Updated to ${status} via Risk Center` }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['risk-alerts'] }),
  });

  const toggleRule = useMutation({
    mutationFn: (r: RiskRule) =>
      apiFetch(`/api/v1/risk/rules/${r.ruleCode}`, {
        method: 'PUT',
        body: JSON.stringify({
          enabled: !r.enabled,
          thresholdValue: r.thresholdValue,
          scoreWeight: r.scoreWeight,
        }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['risk-rules'] }),
  });

  const canManage = hasRole('PLATFORM_ADMIN', 'RISK_ANALYST');

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Risk & Rule Engine Center</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Deterministic rule-based risk alerts with human-readable explanations (simulation rule engine)
          </p>
        </div>
        <select
          aria-label="Filter risk alerts by status"
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm"
        >
          <option value="">All Alert Statuses</option>
          <option value="OPEN">OPEN</option>
          <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          <option value="RESOLVED">RESOLVED</option>
          <option value="DISMISSED">DISMISSED</option>
        </select>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Rule</th>
                <th className="px-4 py-3">Severity</th>
                <th className="px-4 py-3">Explanation</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Created</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
              {alerts?.items.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">No risk alerts found.</td>
                </tr>
              )}
              {alerts?.items.map((a) => (
                <tr key={a.id}>
                  <td className="px-4 py-3 font-mono text-xs">{a.ruleCode}</td>
                  <td className="px-4 py-3"><StatusBadge value={a.severity} /></td>
                  <td className="px-4 py-3 text-xs">{a.explanation}</td>
                  <td className="px-4 py-3"><StatusBadge value={a.status} /></td>
                  <td className="px-4 py-3 text-xs text-slate-500">{formatUtcTimestamp(a.createdAt)}</td>
                  <td className="px-4 py-3 text-right space-x-1.5">
                    {canManage && (a.status === 'OPEN' || a.status === 'ACKNOWLEDGED') && (
                      <>
                        {a.status === 'OPEN' && (
                          <button
                            type="button"
                            onClick={() => updateAlert.mutate({ id: a.id, status: 'ACKNOWLEDGED' })}
                            className="px-2 py-1 rounded border border-slate-300 dark:border-navy-700 text-xs"
                          >
                            Acknowledge
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => updateAlert.mutate({ id: a.id, status: 'RESOLVED' })}
                          className="px-2 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs"
                        >
                          Resolve
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {alerts && (
          <PaginationControls
            page={alerts.page}
            totalPages={alerts.totalPages}
            totalElements={alerts.totalElements}
            onPageChange={setPage}
          />
        )}
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold mb-3">Configured Risk Rules</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {rules?.map((r) => (
            <div key={r.ruleCode} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 text-xs space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-mono font-semibold">{r.ruleCode}</span>
                <div className="flex items-center gap-2">
                  <StatusBadge value={r.severity} />
                  {canManage && (
                    <button
                      type="button"
                      onClick={() => toggleRule.mutate(r)}
                      className="px-2 py-0.5 rounded border border-slate-300 dark:border-navy-700 hover:bg-slate-100 dark:hover:bg-navy-800"
                    >
                      {r.enabled ? 'Disable' : 'Enable'}
                    </button>
                  )}
                </div>
              </div>
              <div className="text-slate-500">{r.description}</div>
              <div className="font-mono text-[11px]">
                Threshold: {r.thresholdValue} • Weight: {r.scoreWeight} • {r.enabled ? 'ENABLED' : 'DISABLED'}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
