/** Single source of truth for job statuses and their badge styling. */
export const JOB_STATUSES = ['PENDING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED']

export const JOB_TYPES = ['SIMULATED', 'PING', 'FAIL']

const BADGE_CLASS = {
  PENDING: 'bg-slate-100 text-slate-700 ring-slate-200',
  QUEUED: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  RUNNING: 'bg-amber-50 text-amber-700 ring-amber-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  FAILED: 'bg-rose-50 text-rose-700 ring-rose-200',
}

export const badgeClass = (status) => BADGE_CLASS[status] ?? BADGE_CLASS.PENDING

/** QUEUED and RUNNING are transient: the worker is still going to act on them. */
export const isActive = (status) => status === 'QUEUED' || status === 'RUNNING'

/** A FAILED job can be retried while it still has attempts, or requeued afterwards. */
export const canRetry = (job) => job.status === 'FAILED' && job.attemptsLeft > 0

export const canRequeue = (job) => job.status === 'FAILED' && job.attemptsLeft === 0

export const canRun = (job) => job.status === 'PENDING' || canRetry(job)