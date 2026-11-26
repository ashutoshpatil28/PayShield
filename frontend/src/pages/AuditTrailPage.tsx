import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { PaginationControls } from '../components/PaginationControls';
import { formatUtcTimestamp } from '../lib/format';

interface AuditEvent {
  id: string;
  actorEmail: string | null;
  actorRoles: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  outcome: string;
  correlationId: string | null;
  createdAt: string;
}

interface PagedAudit {
  items: AuditEvent[];
  page: number;
  totalElements: number;
  totalPages: number;
}

export function AuditTrailPage() {
  const [actionFilter, setActionFilter] = useState('');
  const [outcomeFilter, setOutcomeFilter] = useState('');
  const [page, setPage] = useState(0);

  const { data } = useQuery({
    queryKey: ['audit-events', page, actionFilter, outcomeFilter],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '20' });
      if (actionFilter.trim()) params.set('action', actionFilter.trim());
      if (outcomeFilter) params.set('outcome', outcomeFilter);
      return apiFetch<PagedAudit>(`/api/v1/audit-events?${params.toString()}`);
    },
  });

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Audit Trail</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Append-only record of authentication, RBAC, payment, payout, ledger, and reconciliation actions
        </p>
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          aria-label="Filter audit events by action"
          placeholder="Filter by action (e.g. AUTH_LOGIN, PAYOUT_APPROVED)..."
          value={actionFilter}
          onChange={(e) => {
            setActionFilter(e.target.value);
            setPage(0);
          }}
          className="flex-1 min-w-[240px] px-3 py-2 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm"
        />
        <select
          aria-label="Filter audit events by outcome"
          value={outcomeFilter}
          onChange={(e) => {
            setOutcomeFilter(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-sm"
        >
          <option value="">All Outcomes</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="FAILURE">FAILURE</option>
          <option value="DENIED">DENIED</option>
        </select>
      </div>

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Timestamp</th>
                <th className="px-4 py-3">Actor</th>
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Resource</th>
                <th className="px-4 py-3">Outcome</th>
                <th className="px-4 py-3">Correlation ID</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
              {data?.items.map((a) => (
                <tr key={a.id}>
                  <td className="px-4 py-3 text-xs text-slate-500">{formatUtcTimestamp(a.createdAt)}</td>
                  <td className="px-4 py-3 text-xs">{a.actorEmail || 'SYSTEM'}</td>
                  <td className="px-4 py-3 font-mono text-xs">{a.action}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-500">
                    {a.resourceType} {a.resourceId ? `(${a.resourceId.slice(0, 8)}…)` : ''}
                  </td>
                  <td className="px-4 py-3"><StatusBadge value={a.outcome} /></td>
                  <td className="px-4 py-3 font-mono text-[11px] text-slate-500">{a.correlationId || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && (
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={setPage}
          />
        )}
      </div>
    </div>
  );
}
