export default function ErrorBanner({ message, onDismiss }) {
  if (!message) return null
  return (
    <div
      role="alert"
      className="flex items-start justify-between gap-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3"
    >
      <p className="text-sm text-rose-800">{message}</p>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 text-xs font-medium text-rose-700 hover:underline"
        >
          Dismiss
        </button>
      )}
    </div>
  )
}