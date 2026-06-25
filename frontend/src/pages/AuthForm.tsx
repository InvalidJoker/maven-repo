import { useState, type FormEvent } from 'react'
import { ApiError } from '../api'
import { navigate } from '../router'
import { Button, Card, ErrorText, Input } from '../ui'

interface AuthFormProps {
  title: string
  submitLabel: string
  action: (username: string, password: string) => Promise<void>
  footer: { prompt: string; linkLabel: string; to: string }
}

export function AuthForm({ title, submitLabel, action, footer }: AuthFormProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await action(username, password)
      navigate('/')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4">
      <Card className="w-full max-w-sm p-6">
        <button
          onClick={() => navigate('/')}
          className="mb-4 text-sm text-slate-500 hover:text-slate-300"
        >
          ← Browse repositories
        </button>
        <h1 className="mb-1 text-lg font-semibold text-slate-100">{title}</h1>
        <form onSubmit={onSubmit} className="mt-5 space-y-3">
          <Input
            placeholder="Username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <Input
            type="password"
            placeholder="Password"
            autoComplete={submitLabel === 'Sign in' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <ErrorText>{error}</ErrorText>
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? 'Please wait…' : submitLabel}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-500">
          {footer.prompt}{' '}
          <button onClick={() => navigate(footer.to)} className="text-slate-200 hover:underline">
            {footer.linkLabel}
          </button>
        </p>
      </Card>
    </div>
  )
}
