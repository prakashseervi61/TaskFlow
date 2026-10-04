import { Route, Routes } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import JobDetails from './pages/JobDetails'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/jobs/:id" element={<JobDetails />} />
      <Route path="*" element={<Dashboard />} />
    </Routes>
  )
}