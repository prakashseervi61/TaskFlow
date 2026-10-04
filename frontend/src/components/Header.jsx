export default function Header({ jobCount }) {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <div className="flex items-center gap-3">
          <span className="grid h-8 w-8 place-items-center rounded-md bg-slate-900 text-sm font-semibold text-white">
            TF
          </span>
          <div>
            <h1 className="text-base font-semibold leading-tight text-slate-900">TaskFlow</h1>
            <p className="text-xs text-slate-500">Job Processing Platform</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <span className="hidden text-xs text-slate-500 sm:inline">v0.3</span>
          {/* only shown where the count is actually known */}
          {typeof jobCount === 'number' && (
            <span className="rounded-full bg-slate-100 px-2.5 py-1 font-mono text-xs text-slate-600">
              {jobCount} {jobCount === 1 ? 'job' : 'jobs'}
            </span>
          )}
        </div>
      </div>
    </header>
  )
}