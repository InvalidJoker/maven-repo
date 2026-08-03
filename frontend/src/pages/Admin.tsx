import { useEffect, useState, type FormEvent } from 'react'
import { api, type Repository, type RepositoryType } from '../api'
import { navigate } from '../router'
import { AdminNav } from '../components/AdminNav'
import {
  Button,
  Card,
  ErrorText,
  Input,
  PageHeading,
  Table,
  Td,
  Th,
  TypeBadge,
  VisibilityBadge,
} from '../ui'

const TYPES: { id: RepositoryType; label: string; hint: string }[] = [
  { id: 'MAVEN', label: 'Maven', hint: 'Served at /maven/<repository> for Gradle and Maven.' },
  { id: 'DOCKER', label: 'Docker', hint: 'Served at /v2 for docker, podman and buildx. Names must be lowercase.' },
  { id: 'NPM', label: 'npm', hint: 'Served at /npm/<repository> for npm, pnpm, yarn and bun.' },
]

export function Admin() {
  const [repos, setRepos] = useState<Repository[]>([])
  const [name, setName] = useState('')
  const [type, setType] = useState<RepositoryType>('MAVEN')
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
      await api.createRepository(name.trim(), isPrivate, type)
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
        <form onSubmit={onCreate} className="space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <Input
              placeholder="repository-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="max-w-xs"
            />
            <div className="flex gap-1">
              {TYPES.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => setType(option.id)}
                  className={`rounded px-2 py-1 text-xs transition-colors ${
                    type === option.id ? 'bg-brand-500 text-white' : 'text-neutral-400 hover:bg-neutral-800'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
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
          </div>
          <p className="text-xs text-neutral-500">{TYPES.find((t) => t.id === type)?.hint}</p>
        </form>
      </Card>

      <Table
        head={
          <tr>
            <Th>Name</Th>
            <Th>Type</Th>
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
            <Td />
          </tr>
        ) : (
          repos.map((repo) => (
            <tr key={repo.id} className="hover:bg-neutral-900">
              <Td className="font-medium text-neutral-100">{repo.name}</Td>
              <Td>
                <TypeBadge type={repo.type} />
              </Td>
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
