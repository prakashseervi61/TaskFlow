import { useCallback, useEffect, useMemo, useState } from 'react'
import { toErrorMessage } from '../api/client'
import {
  deleteJob as deleteJobRequest,
  executeJob as executeJobRequest,
  getJobs,
  getStats,
  requeueJob as requeueJobRequest,
} from '../api/jobs'
import { isActive } from '../lib/status'

const POLL_INTERVAL_MS = 1000
const PAGE_SIZE = 20
const MAX_PAGES = 20 // cursor history guard, ~400 jobs reachable

/**
 * Owns the dashboard data: one page of jobs, server-side counts, and the page cursor history.
 *
 * <p>Counts come from `/api/jobs/stats` rather than being derived from the loaded page — with
 * pagination a page-local count would report "20 of 500 jobs". For the same reason polling is
 * driven by the server's `activeCount`, so a QUEUED job on a later page still keeps the view
 * live.
 */
export function useJobs({ pollIntervalMs = POLL_INTERVAL_MS, pageSize = PAGE_SIZE } = {}) {
  const [items, setItems] = useState([])
  const [cursors, setCursors] = useState([null])
  const [pageIndex, setPageIndex] = useState(0)
  const [nextCursor, setNextCursor] = useState(null)
  const [total, setTotal] = useState(0)
  const [stats, setStats] = useState({
    total: 0,
    pending: 0,
    queued: 0,
    running: 0,
    completed: 0,
    failed: 0,
    retrying: 0,
    activeCount: 0,
  })
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState(null)
  const [busyJobId, setBusyJobId] = useState(null)

  const load = useCallback(async () => {
    try {
      const [page, counts] = await Promise.all([
        getJobs({ limit: pageSize, cursor: cursors[pageIndex] ?? undefined }),
        getStats(),
      ])
      setItems(page.items)
      setNextCursor(page.nextCursor)
      setTotal(page.total)
      setStats(counts)
      setError(null)
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to load jobs.'))
    } finally {
      setLoading(false)
    }
  }, [cursors, pageIndex, pageSize])

  useEffect(() => {
    load()
  }, [load])

  // Poll when the server reports active work anywhere, OR when a row on this page is active.
  // Both signals are needed: the server count covers jobs on other pages, while the local
  // check covers the window right after pressing Run, before the counts have caught up.
  const hasActiveJobOnPage = items.some((job) => isActive(job.status))
  const shouldPoll = stats.activeCount > 0 || hasActiveJobOnPage

  useEffect(() => {
    if (!shouldPoll) return undefined
    const timer = setInterval(load, pollIntervalMs)
    return () => clearInterval(timer)
  }, [shouldPoll, pollIntervalMs, load])

  const refresh = useCallback(async () => {
    setRefreshing(true)
    await load()
    setRefreshing(false)
  }, [load])

  // Mutations change the counts without necessarily starting the poll loop (deleting a PENDING
  // job, for instance), so the stat cards are synced explicitly rather than waiting.
  const refreshStats = useCallback(async () => {
    try {
      setStats(await getStats())
    } catch {
      // A failed count refresh is not worth surfacing; the next poll or manual refresh covers it.
    }
  }, [])

  const goToPage = useCallback(
    (index) => {
      if (index < 0 || index >= cursors.length) return
      // Truncate the forward history so paging back and forth stays consistent.
      setCursors((current) => (index + 1 < current.length ? current.slice(0, index + 1) : current))
      setPageIndex(index)
    },
    [cursors.length],
  )

  const goNextPage = useCallback(() => {
    if (!nextCursor) return
    setCursors((current) => {
      const next = [...current]
      next[pageIndex + 1] = nextCursor
      return next
    })
    setPageIndex((index) => index + 1)
  }, [nextCursor, pageIndex])

  const goPreviousPage = useCallback(() => {
    if (pageIndex === 0) return
    setPageIndex((index) => index - 1)
  }, [pageIndex])

  /** Runs or retries a job, replacing it in place so the page does not jump. */
  const executeJob = useCallback(async (id) => {
    setBusyJobId(id)
    try {
      const updated = await executeJobRequest(id)
      setItems((current) => current.map((job) => (job.id === updated.id ? updated : job)))
      setError(null)
      await refreshStats()
      return { ok: true }
    } catch (err) {
      const message = toErrorMessage(err, 'Failed to run the job.')
      setError(message)
      return { ok: false, message }
    } finally {
      setBusyJobId(null)
    }
  }, [refreshStats])

  /** Gives a terminally failed job a fresh budget of attempts. */
  const requeueJob = useCallback(
    async (id) => {
      setBusyJobId(id)
      try {
        const updated = await requeueJobRequest(id)
        setItems((current) => current.map((job) => (job.id === updated.id ? updated : job)))
        setError(null)
        await refreshStats()
        return { ok: true }
      } catch (err) {
        const message = toErrorMessage(err, 'Failed to requeue the job.')
        setError(message)
        return { ok: false, message }
      } finally {
        setBusyJobId(null)
      }
    },
    [refreshStats],
  )

  const deleteJob = useCallback(async (id) => {
    setBusyJobId(id)
    try {
      await deleteJobRequest(id)
      setItems((current) => current.filter((job) => job.id !== id))
      setError(null)
      await refreshStats()
      return { ok: true }
    } catch (err) {
      const message = toErrorMessage(err, 'Failed to delete the job.')
      setError(message)
      return { ok: false, message }
    } finally {
      setBusyJobId(null)
    }
  }, [refreshStats])

  const dismissError = useCallback(() => setError(null), [])

  const paging = useMemo(
    () => ({
      pageIndex,
      hasPrevious: pageIndex > 0,
      hasNext: Boolean(nextCursor),
      total,
      pageCount: Math.max(1, Math.ceil(total / pageSize)),
      canGoForward: pageIndex < MAX_PAGES,
    }),
    [pageIndex, nextCursor, total, pageSize],
  )

  return {
    jobs: items,
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
  }
}