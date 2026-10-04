export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export function formatDuration(job) {
  if (!job.startedAt || !job.completedAt) return '—'
  const ms = new Date(job.completedAt) - new Date(job.startedAt)
  if (Number.isNaN(ms) || ms < 0) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

/** "attempt 2 of 3", or just the count when a single attempt is allowed. */
export function formatAttempts(job) {
  const max = job.maxAttempts ?? 1
  if (max <= 1) return String(job.attemptCount ?? 0)
  return `${job.attemptCount ?? 0} of ${max}`
}

/** Seconds until a scheduled retry becomes eligible, for the "retrying in 12s" hint. */
export function formatCountdown(value, now = Date.now()) {
  if (!value) return null
  const ms = new Date(value).getTime() - now
  if (Number.isNaN(ms) || ms <= 0) return null
  const seconds = Math.ceil(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}