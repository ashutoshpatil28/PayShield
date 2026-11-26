import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Upload } from 'lucide-react';
import { apiFetch, ApiError } from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import { PaginationControls } from '../components/PaginationControls';
import { formatUtcTimestamp } from '../lib/format';

interface ReconSummary {
  totalFiles: number;
  totalImportedRecords: number;
  matchedRecords: number;
  unmatchedRecords: number;
  openCases: number;
  resolvedCases: number;
  completionRatePercent: number;
}

interface ReconCase {
  id: string;
  exceptionCategory: string;
  status: string;
  summary: string;
  createdAt: string;
}

interface PagedCases {
  items: ReconCase[];
  page: number;
  totalElements: number;
  totalPages: number;
}

interface SettlementFile {
  id: string;
  fileName: string;
  providerCode: string;
  totalRecords: number;
  matchedRecords: number;
  unmatchedRecords: number;
  createdAt: string;
}

const SAMPLE_CSV = `provider_reference,merchant_order_ref,amount_minor,fee_minor,currency,settlement_date
SIM_ALPHA_DEMO1,ORD-1001,10000,200,INR,2026-11-26`;

export function ReconciliationPage() {
  const qc = useQueryClient();
  const [csvContent, setCsvContent] = useState(SAMPLE_CSV);
  const [fileName, setFileName] = useState(`settlement_${Date.now()}.csv`);
  const [caseStatusFilter, setCaseStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { data: summary } = useQuery({
    queryKey: ['recon-summary'],
    queryFn: () => apiFetch<ReconSummary>('/api/v1/reconciliation/summary'),
  });

  const { data: files } = useQuery({
    queryKey: ['recon-files'],
    queryFn: () => apiFetch<SettlementFile[]>('/api/v1/reconciliation/files'),
  });

  const { data: cases } = useQuery({
    queryKey: ['recon-cases', page, caseStatusFilter],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '15' });
      if (caseStatusFilter) params.set('status', caseStatusFilter);
      return apiFetch<PagedCases>(`/api/v1/reconciliation/cases?${params.toString()}`);
    },
  });

  const uploadMutation = useMutation({
    mutationFn: () =>
      apiFetch('/api/v1/reconciliation/files', {
        method: 'POST',
        body: JSON.stringify({
          fileName,
          providerCode: 'PROVIDER_ALPHA',
          csvContent,
        }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['recon-summary'] });
      qc.invalidateQueries({ queryKey: ['recon-cases'] });
      qc.invalidateQueries({ queryKey: ['recon-files'] });
      setFileName(`settlement_${Date.now()}.csv`);
      setErrorMsg(null);
    },
    onError: (err) => setErrorMsg(err instanceof ApiError ? err.message : 'Upload failed'),
  });

  const resolveCase = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiFetch(`/api/v1/reconciliation/cases/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status, resolutionNotes: `Marked ${status} from console` }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['recon-summary'] });
      qc.invalidateQueries({ queryKey: ['recon-cases'] });
    },
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Settlement Reconciliation</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Synthetic CSV settlement ingestion, SHA-256 deduplication, and discrepancy case investigation
        </p>
      </div>

      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
            <div className="text-xs text-slate-500">Imported Records</div>
            <div className="text-2xl font-semibold tabular-nums mt-1">{summary.totalImportedRecords}</div>
          </div>
          <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
            <div className="text-xs text-slate-500">Matched</div>
            <div className="text-2xl font-semibold tabular-nums text-emerald-500 mt-1">{summary.matchedRecords}</div>
          </div>
          <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
            <div className="text-xs text-slate-500">Open Cases</div>
            <div className="text-2xl font-semibold tabular-nums text-amber-500 mt-1">{summary.openCases}</div>
          </div>
          <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-4">
            <div className="text-xs text-slate-500">Match Rate</div>
            <div className="text-2xl font-semibold tabular-nums mt-1">{summary.completionRatePercent}%</div>
          </div>
        </div>
      )}

      <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5 space-y-3">
        <h2 className="text-sm font-semibold">Ingest Synthetic Settlement CSV</h2>
        {errorMsg && <div className="p-2.5 rounded bg-rose-500/10 text-rose-400 text-xs">{errorMsg}</div>}
        <input
          aria-label="Settlement file name"
          value={fileName}
          onChange={(e) => setFileName(e.target.value)}
          className="w-full max-w-sm px-3 py-1.5 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-xs font-mono"
        />
        <textarea
          aria-label="Settlement CSV content"
          rows={4}
          value={csvContent}
          onChange={(e) => setCsvContent(e.target.value)}
          className="w-full p-3 rounded-lg bg-slate-50 dark:bg-navy-950 border border-slate-300 dark:border-navy-700 text-xs font-mono"
        />
        <button
          type="button"
          onClick={() => uploadMutation.mutate()}
          disabled={uploadMutation.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium"
        >
          <Upload className="w-4 h-4" />
          {uploadMutation.isPending ? 'Reconciling...' : 'Run Reconciliation'}
        </button>
      </div>

      {files && files.length > 0 && (
        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl p-5 space-y-3">
          <h2 className="text-sm font-semibold">Imported Settlement Files</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            {files.map((f) => (
              <div key={f.id} className="p-3 rounded-lg border border-slate-200 dark:border-navy-700 flex items-center justify-between">
                <div>
                  <div className="font-mono font-medium">{f.fileName}</div>
                  <div className="text-slate-500">{f.providerCode} • {formatUtcTimestamp(f.createdAt)}</div>
                </div>
                <div className="tabular-nums text-right">
                  <div>{f.matchedRecords}/{f.totalRecords} matched</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold">Discrepancy Investigation Cases</h2>
          <select
            aria-label="Filter cases by status"
            value={caseStatusFilter}
            onChange={(e) => {
              setCaseStatusFilter(e.target.value);
              setPage(0);
            }}
            className="px-3 py-1.5 rounded-lg bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 text-xs"
          >
            <option value="">All Case Statuses</option>
            <option value="OPEN">OPEN</option>
            <option value="INVESTIGATING">INVESTIGATING</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="DISMISSED">DISMISSED</option>
          </select>
        </div>

        <div className="bg-white dark:bg-navy-900 border border-slate-200 dark:border-navy-700 rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="bg-slate-50 dark:bg-navy-800/60 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Summary</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Created</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-navy-700">
                {cases?.items.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-500">No reconciliation cases.</td>
                  </tr>
                )}
                {cases?.items.map((c) => (
                  <tr key={c.id}>
                    <td className="px-4 py-3 font-mono text-xs">{c.exceptionCategory}</td>
                    <td className="px-4 py-3 text-xs">{c.summary}</td>
                    <td className="px-4 py-3"><StatusBadge value={c.status} /></td>
                    <td className="px-4 py-3 text-xs text-slate-500">{formatUtcTimestamp(c.createdAt)}</td>
                    <td className="px-4 py-3 text-right space-x-1.5">
                      {c.status === 'OPEN' && (
                        <>
                          <button
                            type="button"
                            onClick={() => resolveCase.mutate({ id: c.id, status: 'INVESTIGATING' })}
                            className="px-2 py-1 rounded border border-slate-300 dark:border-navy-700 text-xs"
                          >
                            Investigate
                          </button>
                          <button
                            type="button"
                            onClick={() => resolveCase.mutate({ id: c.id, status: 'RESOLVED' })}
                            className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs"
                          >
                            Resolve
                          </button>
                        </>
                      )}
                      {c.status === 'INVESTIGATING' && (
                        <button
                          type="button"
                          onClick={() => resolveCase.mutate({ id: c.id, status: 'RESOLVED' })}
                          className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs"
                        >
                          Resolve
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {cases && (
            <PaginationControls
              page={cases.page}
              totalPages={cases.totalPages}
              totalElements={cases.totalElements}
              onPageChange={setPage}
            />
          )}
        </div>
      </div>
    </div>
  );
}
