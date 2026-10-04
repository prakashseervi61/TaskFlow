import { badgeClass } from '../lib/status'

export default function StatusBadge({ status }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${badgeClass(
        status,
      )}`}
    >
      {status === 'QUEUED' && (
        <span className="h-1.5 w-1.5 rounded-full bg-indigo-500" aria-hidden="true" />
      )}
      {status === 'RUNNING' && (
        <span className="h-1.5 w-1.5 rounded-full bg-amber-500" aria-hidden="true" />
      )}
      {status ?? 'UNKNOWN'}
    </span>
  )
}