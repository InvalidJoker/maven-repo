import type { ReactNode } from 'react'
import { useAuth } from '../auth'
import { navigate, useHashRoute, segments } from '../router'
import { Button } from '../ui'

function NavLink({ to, label, active }: { to: string; label: string; active: boolean }) {
  return (
    <button
      onClick={() => navigate(to)}
      className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
        active ? 'bg-neutral-800 text-neutral-100' : 'text-neutral-400 hover:text-neutral-100'
      }`}
    >
      {label}
    </button>
  )
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const route = useHashRoute()
  const top = segments(route)[0] ?? ''

  return (
    <div className="mx-auto flex min-h-full max-w-6xl flex-col px-4">
      <header className="flex items-center justify-between border-b border-neutral-800 py-4">
        <div className="flex items-center gap-1">
          <button onClick={() => navigate('/')} className="mr-4 font-semibold tracking-tight text-neutral-100">
            maven<span className="text-neutral-500">/repo</span>
          </button>
          <NavLink to="/" label="Repositories" active={top === ''} />
          {user && <NavLink to="/tokens" label="Tokens" active={top === 'tokens'} />}
          {user?.admin && <NavLink to="/admin" label="Admin" active={top === 'admin'} />}
        </div>
        <div className="flex items-center gap-3">
          {user ? (
            <>
              <span className="text-sm text-neutral-500">{user.username}</span>
              <Button variant="ghost" onClick={logout}>
                Sign out
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" onClick={() => navigate('/login')}>
                Sign in
              </Button>
              <Button onClick={() => navigate('/register')}>Register</Button>
            </>
          )}
        </div>
      </header>
      <main className="flex-1 py-8">{children}</main>
    </div>
  )
}
