export function StatusBadge({ value }: { value: string }) {
  const norm = value.toUpperCase();
  let cls = 'bg-slate-500/10 text-slate-600 dark:text-slate-300 border-slate-500/20';

  if (
    ['SUCCEEDED', 'UP', 'ACTIVE', 'EXECUTED', 'APPROVED', 'RESOLVED', 'MATCHED', 'CLOSED', 'SUCCESS', 'PROCESSED'].includes(
      norm
    )
  ) {
    cls = 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/25';
  } else if (
    ['UNKNOWN', 'PENDING_MAKER_CHECKER', 'PROCESSING', 'INVESTIGATING', 'ACKNOWLEDGED', 'HALF_OPEN', 'LOW', 'MEDIUM'].includes(
      norm
    )
  ) {
    cls = 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/25';
  } else if (
    ['FAILED', 'REJECTED', 'OPEN', 'UNMATCHED', 'DISCREPANCY', 'HIGH', 'CRITICAL', 'DENIED', 'FAILURE', 'DOWN'].includes(
      norm
    )
  ) {
    cls = 'bg-rose-500/10 text-rose-700 dark:text-rose-400 border-rose-500/25';
  }

  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium border ${cls}`}>
      {value}
    </span>
  );
}
