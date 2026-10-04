import { api } from './client'

/**
 * One page of jobs, newest first.
 * @param {{limit?: number, cursor?: string}} page
 */
export const getJobs = async ({ limit, cursor } = {}) => {
  const { data } = await api.get('/api/jobs', { params: { limit, cursor } })
  return data
}

export const getJob = async (id) => {
  const { data } = await api.get(`/api/jobs/${id}`)
  return data
}

export const getStats = async () => {
  const { data } = await api.get('/api/jobs/stats')
  return data
}

export const getDeadLettered = async () => {
  const { data } = await api.get('/api/jobs/dead-letter')
  return data
}

export const createJob = async ({ name, description, jobType, payload, maxAttempts }) => {
  const { data } = await api.post('/api/jobs', { name, description, jobType, payload, maxAttempts })
  return data
}

export const executeJob = async (id) => {
  const { data } = await api.post(`/api/jobs/${id}/execute`)
  return data
}

export const requeueJob = async (id) => {
  const { data } = await api.post(`/api/jobs/${id}/requeue`)
  return data
}

export const deleteJob = async (id) => {
  await api.delete(`/api/jobs/${id}`)
}