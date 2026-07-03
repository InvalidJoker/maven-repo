import { useEffect, useState, type FormEvent } from 'react'
import { api, type Repository } from '../api'
import { navigate } from '../router'
import { AdminNav } from '../components/AdminNav'
import { Button, Card, ErrorText, Input, PageHeading, Table, Td, Th, VisibilityBadge } from '../ui'

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
          <label className="flex items-center gap-2 text-sm text-neutral-300">
            <input
              type="checkbox"
              checked={isPrivate}
              onChange={(e) => setPrivate(e.target.checked)}
              className="accent-brand-500"
            />
            Private
          </label>
          <Button type="submit" disabled={busy}>
            Create
          </Button>
          <ErrorText>{error}</ErrorText>
        </form>
      </Card>

      <Table
        head={
          <tr>
            <Th>Name</Th>
            <Th>Visibility</Th>
            <Th className="text-right">Actions</Th>
          </tr>
        }
      >
        {repos.length === 0 ? (
          <tr>
            <Td className="text-neutral-500" >
              No repositories yet.
            </Td>
            <Td />
            <Td />
          </tr>
        ) : (
          repos.map((repo) => (
            <tr key={repo.id} className="hover:bg-neutral-900">
              <Td className="font-medium text-neutral-100">{repo.name}</Td>
              <Td>
                <VisibilityBadge isPrivate={repo.private} />
              </Td>
              <Td className="text-right">
                <Button
                  variant="ghost"
                  onClick={() => navigate(`/admin/repos/${encodeURIComponent(repo.name)}`)}
                >
                  Manage access
                </Button>
              </Td>
            </tr>
          ))
        )}
      </Table>
    </div>
  )
}
