import { useEffect, useState, type FormEvent } from 'react'
import { api, type Repository } from '../api'
import { navigate } from '../router'
import { AdminNav } from '../components/AdminNav'
import { Button, Card, ErrorText, Input, PageHeading, VisibilityBadge } from '../ui'

export function Admin() {
  const [repos, setRepos] = useState<Repository[]>([])
  const [name, setName] = useState('')
  const [isPrivate, setPrivate] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const reload = () => {
    api.repositories().then(setRepos).catch(() => setError('Failed to load repositories'))
  }

  useEffect(reload, [])

  const onCreate = async (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    setError('')
    try {
      await api.createRepository(name.trim(), isPrivate)
      setName('')
      setPrivate(false)
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create repository')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <AdminNav active="repositories" />
      <PageHeading title="Repositories" subtitle="Create repositories and manage who can access them." />

      <Card className="mb-6 p-4">
        <form onSubmit={onCreate} className="flex flex-wrap items-center gap-3">
          <Input
            placeholder="repository-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="max-w-xs"
          />
          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={isPrivate}
              onChange={(e) => setPrivate(e.target.checked)}
              className="accent-slate-300"
            />
            Private
          </label>
          <Button type="submit" disabled={busy}>
            Create
          </Button>
          <ErrorText>{error}</ErrorText>
        </form>
      </Card>

      <div className="space-y-2">
        {repos.map((repo) => (
          <Card key={repo.id} className="flex items-center justify-between p-4">
            <div className="flex items-center gap-2">
              <span className="font-medium text-slate-100">{repo.name}</span>
              <VisibilityBadge isPrivate={repo.private} />
            </div>
            <Button variant="ghost" onClick={() => navigate(`/admin/repos/${encodeURIComponent(repo.name)}`)}>
              Manage access
            </Button>
          </Card>
        ))}
      </div>
    </div>
  )
}
