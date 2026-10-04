import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
})

/** Turns any Axios failure into a readable message for the UI. */
export function toErrorMessage(error, fallback = 'Something went wrong.') {
  const payload = error?.response?.data
  if (payload?.message) {
    const fieldErrors = payload.fieldErrors?.errors
    return fieldErrors ? `${payload.message}: ${fieldErrors}` : payload.message
  }
  if (error?.code === 'ECONNABORTED') return 'The request timed out.'
  if (error?.request) return `Cannot reach the API at ${baseURL}. Is the backend running?`
  return error?.message ?? fallback
}