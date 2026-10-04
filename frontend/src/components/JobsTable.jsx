import { Link } from 'react-router-dom'
import { formatAttempts, formatCountdown, formatDateTime } from '../lib/format'
import { canRequeue, canRetry, canRun } from '../lib/status'
import Pagination from './Pagination'
import StatusBadge from './StatusBadge'

function EmptyState() {
  return (
    <div className="px-4 py-12 text-center">
      <p className="text-sm font-medium text-slate-700">No jobs yet</p>
      <p className="mt-1 text-xs text-slate-500">Create your first job using the form above.</p>
    </div>
  )
}

function SkeletonRows({ rows = 3 }) {
  return (
    <div className="divide-y divide-slate-100">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-4 px-4 py-3">
          <div className="h-3 w-10 rounded bg-slate-100" />
          <div className="h-3 flex-1 rounded bg-slate-100" />
          <div className="h-3 w-20 rounded bg-slate-100" />
          <div className="h-3 w-32 rounded bg-slate-100" />
        </div>
      ))}
    </div>
  )
}

/** Shows the retry countdown while a job waits out its backoff. */
function RetryHint({ job }) {
  if (job.status !== 'QUEUED' || !job.nextAttemptAt) return null
  const countdown = formatCountdown(job.nextAttemptAt)
  if (!countdown) return null
  return <span className="ml-1 text-[11px] text-indigo-600">retry in {countdown}</span>
}

export default function JobsTable({
  jobs,
  loading,
  refreshing,
  busyJobId,
  paging,
  onRun,
  onRequeue,
  onDelete,
  onPreviousPage,
  onNextPage,
  onGoToPage,
}) {
  return (
    <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">Jobs</h2>
        <span className="text-xs text-slate-500">{refreshing ? 'Refreshing…' : 'Newest first'}</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[64rem] border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
              <th scope="col" className="px-4 py-2 font-medium">ID</th>
              <th scope="col" className="px-4 py-2 font-medium">Name</th>
              <th scope="col" className="px-4 py-2 font-medium">Type</th>
              <th scope="col" className="px-4 py-2 font-medium">Status</th>
              <th scope="col" className="px-4 py-2 font-medium">Attempts</th>
              <th scope="col" className="px-4 py-2 font-medium">Created</th>
              <th scope="col" className="px-4 py-2 font-medium">Completed</th>
              <th scope="col" className="px-4 py-2 text-right font-medium">Actions</th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading && jobs.length === 0 && <SkeletonRows />}

            {!loading && jobs.length === 0 && (
              <tr>
                <td colSpan={8}>
                  <EmptyState />
                </td>
              </tr>
            )}

            {jobs.map((job) => {
              const isBusy = busyJobId === job.id
              return (
                <tr key={job.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2.5 font-mono text-xs text-slate-500">#{job.id}</td>
                  <td className="px-4 py-2.5">
                    <Link
                      to={`/jobs/${job.id}`}
                      className="font-medium text-slate-900 hover:text-indigo-700 hover:underline"
                    >
                      {job.name}
                    </Link>
                    <p className="max-w-xs truncate text-xs text-slate-500" title={job.description}>
                      {job.description}
                    </p>
                  </td>
                  <td className="px-4 py-2.5">
                    <span className="font-mono text-xs text-slate-600">{job.jobType}</span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5">
                    <StatusBadge status={job.status} />
                    <RetryHint job={job} />
                  </td>
                  <td className="px-4 py-2.5">
                    <span
                      className={`font-mono text-xs ${
                        job.status === 'FAILED' ? 'text-rose-600' : 'text-slate-500'
                      }`}
                    >
                      {formatAttempts(job)}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 font-mono text-xs text-slate-500">
                    {formatDateTime(job.createdAt)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2.5 font-mono text-xs text-slate-500">
                    {formatDateTime(job.completedAt)}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center justify-end gap-2">
                      {canRequeue(job) ? (
                        <button
                          type="button"
                          onClick={() => onRequeue(job.id)}
                          disabled={isBusy}
                          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                        >
                          Requeue
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onRun(job.id)}
                          disabled={!canRun(job) || isBusy}
                          title={
                            canRun(job) ? 'Run job' : 'This job cannot be run from its current state'
                          }
                          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-300"
                        >
                          {isBusy ? 'Working…' : canRetry(job) ? 'Retry' : 'Run'}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onDelete(job.id)}
                        disabled={isBusy}
                        className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-rose-50 hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {!loading && jobs.length > 0 && (
        <Pagination
          paging={paging}
          onPrevious={onPreviousPage}
          onNext={onNextPage}
          onGoTo={onGoToPage}
        />
      )}
    </section>
  )
}