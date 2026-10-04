import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toErrorMessage } from '../api/client'
import { deleteJob as deleteJobRequest, executeJob as executeJobRequest, getJob, requeueJob as requeueJobRequest } from '../api/jobs'
import ErrorBanner from '../components/ErrorBanner'
import Header from '../components/Header'
import StatusBadge from '../components/StatusBadge'
import { formatAttempts, formatCountdown, formatDateTime, formatDuration } from '../lib/format'
import { canRequeue, canRetry, canRun, isActive } from '../lib/status'

function DetailRow({ label, children }) {
  return (
    <div className="grid gap-1 border-b border-slate-100 px-4 py-3 last:border-b-0 sm:grid-cols-3 sm:gap-4">
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-sm text-slate-900 sm:col-span-2">{children}</dd>
    </div>
  )
}

const LIFECYCLE = ['PENDING', 'QUEUED', 'RUNNING', 'COMPLETED']

export default function JobDetails() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [job, setJob] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setJob(await getJob(id))
      setError(null)
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to load the job.'))
      setJob(null)
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  // A response for a previous id must not be shown for the current one
  const loading = job?.id !== Number(id)

  // keep the view in sync while the worker is still going to act on the job
  useEffect(() => {
    if (!isActive(job?.status)) return undefined
    const timer = setInterval(load, 1000)
    return () => clearInterval(timer)
  }, [job?.status, load])

  async function handleRun() {
    setBusy(true)
    try {
      setJob(await executeJobRequest(id))
      setError(null)
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to run the job.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleRequeue() {
    setBusy(true)
    try {
      setJob(await requeueJobRequest(id))
      setError(null)
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to requeue the job.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    setBusy(true)
    try {
      await deleteJobRequest(id)
      navigate('/')
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to delete the job.'))
      setBusy(false)
    }
  }

  const stageIndex = job ? LIFECYCLE.indexOf(job.status) : -1
  const retryIn = formatCountdown(job?.nextAttemptAt)
  const actionLabel = canRetry(job) ? 'Retry job' : 'Run job'

  return (
    <div className="min-h-screen">
      <Header jobCount={0} />

      <main className="mx-auto max-w-3xl space-y-4 px-6 py-6">
        <Link to="/" className="text-sm text-slate-600 hover:text-slate-900 hover:underline">
          ← Back to dashboard
        </Link>

        <ErrorBanner message={error} />

        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <div className="flex items-center gap-3">
              <h1 className="text-sm font-semibold text-slate-900">Job #{id}</h1>
              {job && <StatusBadge status={job.status} />}
            </div>

            {job && (
              <div className="flex items-center gap-2">
                {canRequeue(job) ? (
                  <button
                    type="button"
                    onClick={handleRequeue}
                    disabled={busy}
                    className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-40"
                  >
                    {busy ? 'Working…' : 'Requeue'}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleRun}
                    disabled={!canRun(job) || busy}
                    title={canRun(job) ? undefined : 'This job cannot be run from its current state'}
                    className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {busy ? 'Working…' : actionLabel}
                  </button>
                )}
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={busy}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"
                >
                  Delete
                </button>
              </div>
            )}
          </div>

          {loading && <p className="px-4 py-8 text-center text-sm text-slate-500">Loading job…</p>}

          {!loading && job && (
            <>
              <div className="flex items-center gap-1 border-b border-slate-100 px-4 py-3">
                {job.status === 'FAILED' ? (
                  <p className="font-mono text-xs text-rose-600">
                    PENDING → QUEUED → RUNNING → FAILED
                    {job.lastError ? ` · ${job.lastError}` : ''}
                  </p>
                ) : (
                  LIFECYCLE.map((stage, index) => (
                    <div key={stage} className="flex items-center gap-1">
                      <span
                        className={`rounded px-1.5 py-0.5 font-mono text-[11px] ${
                          index <= stageIndex ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-400'
                        }`}
                      >
                        {stage}
                      </span>
                      {index < LIFECYCLE.length - 1 && <span className="text-slate-300">→</span>}
                    </div>
                  ))
                )}
                {retryIn && (
                  <p className="ml-auto font-mono text-xs text-indigo-600">next attempt in {retryIn}</p>
                )}
              </div>

              <dl>
                <DetailRow label="Name">{job.name}</DetailRow>
                <DetailRow label="Description">
                  <span className="whitespace-pre-wrap">{job.description}</span>
                </DetailRow>
                <DetailRow label="Job type">
                  <span className="font-mono text-xs">{job.jobType}</span>
                </DetailRow>
                <DetailRow label="Payload">
                  {job.payload && Object.keys(job.payload).length > 0 ? (
                    <pre className="overflow-x-auto rounded bg-slate-50 p-2 font-mono text-xs">
                      {JSON.stringify(job.payload, null, 2)}
                    </pre>
                  ) : (
                    <span className="text-slate-400">—</span>
                  )}
                </DetailRow>
                <DetailRow label="Attempts">
                  <span className="font-mono text-xs">
                    {formatAttempts(job)}
                    {job.attemptsLeft > 0 && (
                      <span className="ml-2 text-slate-500">({job.attemptsLeft} left)</span>
                    )}
                  </span>
                </DetailRow>
                <DetailRow label="Created at">
                  <span className="font-mono text-xs">{formatDateTime(job.createdAt)}</span>
                </DetailRow>
                <DetailRow label="Queued at">
                  <span className="font-mono text-xs">{formatDateTime(job.queuedAt)}</span>
                </DetailRow>
                <DetailRow label="Next attempt at">
                  <span className="font-mono text-xs">{formatDateTime(job.nextAttemptAt)}</span>
                </DetailRow>
                <DetailRow label="Started at">
                  <span className="font-mono text-xs">{formatDateTime(job.startedAt)}</span>
                </DetailRow>
                <DetailRow label="Completed at">
                  <span className="font-mono text-xs">{formatDateTime(job.completedAt)}</span>
                </DetailRow>
                <DetailRow label="Duration">{formatDuration(job)}</DetailRow>
                {job.lastError && <DetailRow label="Last error">{job.lastError}</DetailRow>}
                {job.failureReason && <DetailRow label="Failure reason">{job.failureReason}</DetailRow>}
              </dl>
            </>
          )}

          {!loading && !job && (
            <p className="px-4 py-8 text-center text-sm text-slate-500">Job not found.</p>
          )}
        </section>
      </main>
    </div>
  )
}