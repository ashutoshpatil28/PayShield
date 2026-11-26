import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { formatUtcTimestamp } from '../lib/format';

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
  const qc = useQueryClient();

  const { data: alerts } = useQuery({
    queryKey: ['risk-alerts'],
    queryFn: () => apiFetch<{ items: RiskAlert[] }>('/api/v1/risk/alerts?size=30'),
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

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Risk & Rule Engine Center</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Deterministic rule-based risk alerts with human-readable explanations (simulation rule engine)
        </p>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-x-auto">
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
                <td colSpan={6} className="px-4 py-8 text-center text-slate-500">No risk alerts triggered yet.</td>
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
                  {a.status === 'OPEN' && (
                    <>
                      <button
                        type="button"
                        onClick={() => updateAlert.mutate({ id: a.id, status: 'ACKNOWLEDGED' })}
                        className="px-2 py-1 rounded border border-slate-300 dark:border-navy-700 text-xs"
                      >
                        Acknowledge
                      </button>
                      <button
                        type="button"
                        onClick={() => updateAlert.mutate({ id: a.id, status: 'RESOLVED' })}
                        className="px-2 py-1 rounded bg-emerald-600 text-white text-xs"
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

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold mb-3">Configured Risk Rules</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {rules?.map((r) => (
            <div key={r.ruleCode} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 text-xs space-y-1">
              <div className="flex items-center justify-between">
                <span className="font-mono font-semibold">{r.ruleCode}</span>
                <StatusBadge value={r.severity} />
              </div>
              <div className="text-slate-500">{r.description}</div>
              <div className="font-mono text-[11px]">Threshold: {r.thresholdValue} • Weight: {r.scoreWeight}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
