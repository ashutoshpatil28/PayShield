import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationControlsProps {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (nextPage: number) => void;
}

export function PaginationControls({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: PaginationControlsProps) {
  if (totalElements === 0) return null;
  const safeTotalPages = Math.max(1, totalPages);

  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-slate-200 dark:border-navy-700 text-xs text-slate-500 dark:text-slate-400">
      <div className="tabular-nums">
        Page <span className="font-medium text-slate-900 dark:text-slate-200">{page + 1}</span> of{' '}
        <span className="font-medium text-slate-900 dark:text-slate-200">{safeTotalPages}</span> •{' '}
        {totalElements} total records
      </div>
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          aria-label="Previous page"
          className="inline-flex items-center gap-1 px-2.5 py-1 rounded border border-slate-200 dark:border-navy-700 disabled:opacity-40 hover:bg-slate-100 dark:hover:bg-navy-800"
        >
          <ChevronLeft className="w-3.5 h-3.5" /> Prev
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= safeTotalPages}
          aria-label="Next page"
          className="inline-flex items-center gap-1 px-2.5 py-1 rounded border border-slate-200 dark:border-navy-700 disabled:opacity-40 hover:bg-slate-100 dark:hover:bg-navy-800"
        >
          Next <ChevronRight className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
}
