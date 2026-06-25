import type { ReactNode } from 'react'
import { useAuth } from './auth'
import { segments, useHashRoute } from './router'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Dashboard } from './pages/Dashboard'
import { Tokens } from './pages/Tokens'
import { Admin } from './pages/Admin'
import { RepoPermissions } from './pages/RepoPermissions'

function Centered({ children }: { children: ReactNode }) {
  return <div className="flex min-h-full items-center justify-center text-sm text-neutral-500">{children}</div>
}

function resolve(route: string, isAdmin: boolean): ReactNode {
  const parts = segments(route)

  if (parts[0] === 'tokens') return <Tokens />

  if (parts[0] === 'admin') {
    if (!isAdmin) return <Centered>You don't have access to this page.</Centered>
    if (parts[1] === 'repos' && parts[2]) return <RepoPermissions repo={parts[2]} />
    return <Admin />
  }

  return <Dashboard />
}

export default function App() {
  const { user, loading } = useAuth()
  const route = useHashRoute()

  if (loading) return <Centered>Loading…</Centered>
  if (!user) return <Login />

  return <Layout>{resolve(route, user.admin)}</Layout>
}
