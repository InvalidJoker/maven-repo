import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, type User } from '../api'
import { AdminNav } from '../components/AdminNav'
import { Badge, Button, Card, ErrorText, Input, PageHeading } from '../ui'

export function AdminUsers() {
  const [users, setUsers] = useState<User[]>([])
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [admin, setAdmin] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)

  const reload = () => {
    api.users().then(setUsers).catch(() => setError('Failed to load users'))
  }

  useEffect(reload, [])

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api.createUser(username.trim(), password, admin)
      setUsername('')
      setPassword('')
      setAdmin(false)
      reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create user')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <AdminNav active="users" />
      <PageHeading title="Users" subtitle="Create and manage accounts." />

      <Card className="mb-6 p-4">
        <form onSubmit={onCreate} className="flex flex-wrap items-center gap-3">
          <Input
            placeholder="username"
            autoComplete="off"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="max-w-xs"
          />
          <Input
            type="password"
            placeholder="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="max-w-xs"
          />
          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={admin}
              onChange={(e) => setAdmin(e.target.checked)}
              className="accent-indigo-500"
            />
            Administrator
          </label>
          <Button type="submit" disabled={busy}>
            Create user
          </Button>
          <ErrorText>{error}</ErrorText>
        </form>
      </Card>

      <div className="space-y-2">
        {users.map((user) =>
          editingId === user.id ? (
            <EditUserRow key={user.id} user={user} onClose={() => setEditingId(null)} onSaved={reload} />
          ) : (
            <Card key={user.id} className="flex items-center justify-between p-4">
              <div className="flex items-center gap-2">
                <span className="font-medium text-slate-100">{user.username}</span>
                {user.admin && <Badge tone="violet">admin</Badge>}
              </div>
              <Button variant="ghost" onClick={() => setEditingId(user.id)}>
                Edit
              </Button>
            </Card>
          ),
        )}
      </div>
    </div>
  )
}

function EditUserRow({
  user,
  onClose,
  onSaved,
}: {
  user: User
  onClose: () => void
  onSaved: () => void
}) {
  const [admin, setAdmin] = useState(user.admin)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const onSave = async () => {
    setBusy(true)
    setError('')
    try {
      const changes: { admin?: boolean; password?: string } = {}
      if (admin !== user.admin) changes.admin = admin
      if (password.length > 0) changes.password = password
      if (Object.keys(changes).length === 0) {
        onClose()
        return
      }
      await api.updateUser(user.id, changes)
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update user')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-center gap-3">
        <span className="font-medium text-slate-100">{user.username}</span>
        <Input
          type="password"
          placeholder="new password (leave blank to keep)"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="max-w-xs"
        />
        <label className="flex items-center gap-2 text-sm text-slate-300">
          <input
            type="checkbox"
            checked={admin}
            onChange={(e) => setAdmin(e.target.checked)}
            className="accent-indigo-500"
          />
          Administrator
        </label>
        <Button onClick={onSave} disabled={busy}>
          Save
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Cancel
        </Button>
        <ErrorText>{error}</ErrorText>
      </div>
    </Card>
  )
}
