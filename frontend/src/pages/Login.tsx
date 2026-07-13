import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth'
import { useInstance } from '../instance'
import { ApiError } from '../api'
import { navigate } from '../router'
import { Button, Card, ErrorText, Input } from '../ui'

export function Login() {
  const { login } = useAuth()
  const { oidc, oidcLabel } = useInstance()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(username, password)
      navigate('/')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-1 items-center justify-center px-4">
      <Card className="w-full max-w-sm p-6">
        <button
          onClick={() => navigate('/')}
          className="mb-4 text-sm text-neutral-500 hover:text-neutral-300"
        >
          ← Browse repositories
        </button>
        <h1 className="mb-1 text-lg font-semibold text-neutral-100">Sign in</h1>
        <p className="mb-5 text-sm text-neutral-500">Accounts are created by an administrator.</p>
        <form onSubmit={onSubmit} className="space-y-3">
          <Input
            placeholder="Username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <Input
            type="password"
            placeholder="Password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <ErrorText>{error}</ErrorText>
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>

        {oidc && (
          <>
            <div className="my-4 flex items-center gap-3 text-xs text-neutral-600">
              <span className="h-px flex-1 bg-neutral-800" />
              or
              <span className="h-px flex-1 bg-neutral-800" />
            </div>
            <Button
              variant="ghost"
              className="w-full"
              onClick={() => {
                window.location.href = '/auth/oidc/login'
              }}
            >
              {oidcLabel ?? 'Sign in with SSO'}
            </Button>
          </>
        )}
      </Card>
    </div>
  )
}
