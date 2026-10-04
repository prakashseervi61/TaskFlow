export default function Pagination({ paging, onPrevious, onNext, onGoTo }) {
  const { pageIndex, hasPrevious, hasNext, pageCount, total, canGoForward } = paging
  const first = pageIndex * 20 + 1
  const last = Math.min((pageIndex + 1) * 20, total)

  return (
    <div className="flex items-center justify-between gap-4 border-t border-slate-200 px-4 py-2.5">
      <p className="text-xs text-slate-500">
        {total === 0 ? 'No jobs' : `Showing ${first}–${last} of ${total}`}
      </p>

      <div className="flex items-center gap-2">
        {pageIndex > 1 && (
          <button
            type="button"
            onClick={() => onGoTo(0)}
            className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
          >
            First
          </button>
        )}
        <button
          type="button"
          onClick={onPrevious}
          disabled={!hasPrevious}
          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Previous
        </button>
        <span className="text-xs text-slate-500">
          Page {pageIndex + 1} of {pageCount}
        </span>
        <button
          type="button"
          onClick={onNext}
          disabled={!hasNext || !canGoForward}
          title={hasNext && !canGoForward ? 'Reached the paging depth limit' : undefined}
          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  )
}
