import { useState } from 'react'
import { toErrorMessage } from '../api/client'
import { createJob } from '../api/jobs'
import { JOB_TYPES } from '../lib/status'

const MAX_NAME = 120
const MAX_DESCRIPTION = 2000
const MAX_ATTEMPTS = 10

/** jobType -> what the worker will do, shown next to the picker. */
const TYPE_HINTS = {
  SIMULATED: 'Sleeps briefly, then completes.',
  PING: 'Completes immediately.',
  FAIL: 'Always fails, so retries and the dead letter queue can be exercised.',
}

export default function CreateJobForm({ onCreated }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [jobType, setJobType] = useState('SIMULATED')
  const [payload, setPayload] = useState('')
  const [maxAttempts, setMaxAttempts] = useState(3)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!name.trim() || !description.trim()) {
      setError('Name and description are both required.')
      return
    }

    let parsedPayload
    if (payload.trim()) {
      try {
        parsedPayload = JSON.parse(payload)
      } catch {
        setError('Payload must be valid JSON.')
        return
      }
      if (parsedPayload === null || Array.isArray(parsedPayload) || typeof parsedPayload !== 'object') {
        setError('Payload must be a JSON object, e.g. {"region":"eu"}.')
        return
      }
    }

    setSubmitting(true)
    setError(null)
    try {
      const job = await createJob({
        name: name.trim(),
        description: description.trim(),
        jobType,
        payload: parsedPayload,
        maxAttempts,
      })
      setName('')
      setDescription('')
      setPayload('')
      onCreated(job)
    } catch (err) {
      setError(toErrorMessage(err, 'Failed to create the job.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">Create job</h2>
        <p className="mt-0.5 text-xs text-slate-500">
          New jobs start in <span className="font-mono">PENDING</span> until you run them.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-700">Name</span>
            <input
              type="text"
              value={name}
              maxLength={MAX_NAME}
              onChange={(event) => setName(event.target.value)}
              placeholder="nightly-report"
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm placeholder:text-slate-400 focus:border-slate-900 focus:outline-none"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-700">Description</span>
            <input
              type="text"
              value={description}
              maxLength={MAX_DESCRIPTION}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Builds and emails the daily report"
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm placeholder:text-slate-400 focus:border-slate-900 focus:outline-none"
            />
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-700">Job type</span>
            <select
              value={jobType}
              onChange={(event) => setJobType(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-slate-900 focus:outline-none"
            >
              {JOB_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-700">Max attempts</span>
            <input
              type="number"
              min={1}
              max={MAX_ATTEMPTS}
              value={maxAttempts}
              onChange={(event) => setMaxAttempts(Number(event.target.value))}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-slate-900 focus:outline-none"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-700">
              Payload <span className="font-normal text-slate-400">(optional JSON)</span>
            </span>
            <input
              type="text"
              value={payload}
              onChange={(event) => setPayload(event.target.value)}
              placeholder='{"region":"eu"}'
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-xs placeholder:text-slate-400 focus:border-slate-900 focus:outline-none"
            />
          </label>
        </div>

        <p className="text-xs text-slate-500">{TYPE_HINTS[jobType]}</p>

        {error && (
          <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-700">
            {error}
          </p>
        )}

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Creating…' : 'Create job'}
          </button>
        </div>
      </form>
    </section>
  )
}