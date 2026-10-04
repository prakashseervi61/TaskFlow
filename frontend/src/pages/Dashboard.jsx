import { useState } from 'react'
import CreateJobForm from '../components/CreateJobForm'
import ErrorBanner from '../components/ErrorBanner'
import Header from '../components/Header'
import JobStatistics from '../components/JobStatistics'
import JobsTable from '../components/JobsTable'
import { useJobs } from '../hooks/useJobs'

export default function Dashboard() {
  const {
    jobs,
    stats,
    paging,
    loading,
    refreshing,
    error,
    busyJobId,
    refresh,
    goToPage,
    goNextPage,
    goPreviousPage,
    executeJob,
    requeueJob,
    deleteJob,
    dismissError,
  } = useJobs()

  const [pendingDeleteId, setPendingDeleteId] = useState(null)

  async function handleCreate() {
    setPendingDeleteId(null)
    // Jump back to page 1 so the new job is visible.
    goToPage(0)
    await refresh()
  }

  async function handleDelete(id) {
    if (pendingDeleteId !== id) {
      setPendingDeleteId(id)
      return
    }
    setPendingDeleteId(null)
    await deleteJob(id)
  }

  return (
    <div className="min-h-screen">
      <Header jobCount={stats.total} />

      <main className="mx-auto max-w-6xl space-y-4 px-6 py-6">
        <ErrorBanner message={error} onDismiss={dismissError} />

        <JobStatistics stats={stats} />

        <CreateJobForm onCreated={handleCreate} />

        <JobsTable
          jobs={jobs}
          loading={loading}
          refreshing={refreshing}
          busyJobId={busyJobId}
          paging={paging}
          onRun={executeJob}
          onRequeue={requeueJob}
          onDelete={handleDelete}
          onPreviousPage={goPreviousPage}
          onNextPage={goNextPage}
          onGoToPage={goToPage}
        />

        {pendingDeleteId !== null && (
          <p className="text-right text-xs text-slate-500">
            Click <span className="font-medium text-slate-700">Delete</span> again on job #
            {pendingDeleteId} to confirm.
          </p>
        )}
      </main>
    </div>
  )
}