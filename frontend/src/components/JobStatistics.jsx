function StatCard({ label, value, accent }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 font-mono text-2xl font-semibold ${accent}`}>{value}</p>
    </div>
  )
}

/**
 * Counts come from the server. Deriving them from the loaded page would report page-local
 * numbers once pagination is in play.
 */
export default function JobStatistics({ stats }) {
  return (
    <section aria-label="Job statistics" className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
      <StatCard label="Total" value={stats.total} accent="text-slate-900" />
      <StatCard label="Pending" value={stats.pending} accent="text-slate-600" />
      <StatCard label="Queued" value={stats.queued} accent="text-indigo-600" />
      <StatCard label="Running" value={stats.running} accent="text-amber-600" />
      <StatCard label="Completed" value={stats.completed} accent="text-emerald-600" />
      <StatCard label="Failed" value={stats.failed} accent="text-rose-600" />
      <StatCard label="Retrying" value={stats.retrying} accent="text-slate-500" />
      <StatCard label="Active" value={stats.activeCount} accent="text-slate-900" />
    </section>
  )
}