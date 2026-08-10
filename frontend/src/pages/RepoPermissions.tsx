import { useEffect, useState, type FormEvent } from 'react'
import { api, type Permission, type Repository, type RepositoryPermission } from '../api'
import { navigate } from '../router'
import { UpstreamList } from '../components/UpstreamList'
import { parseUpstreams } from '../upstreams'
import { Button, Card, ErrorText, Input, PageHeading, PermissionBadge, Table, Td, Th } from '../ui'

function MirrorSettings({ repository, onSaved }: { repository: Repository; onSaved: () => void }) {
  const [remotes, setRemotes] = useState(repository.remoteUrls.join('\n'))
  const [ttl, setTtl] = useState(String(repository.cacheTtlSeconds))
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')

  const onSave = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setStatus('')
    try {
      await api.updateRepository(repository.name, {
        remoteUrls: parseUpstreams(remotes),
        cacheTtlSeconds: Number(ttl),
      })
      setStatus('Saved.')
      onSaved()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save')
    }
  }

  const onClear = async () => {
    if (!confirm(`Delete everything ${repository.name} has mirrored so far?`)) return
    setError('')
    setStatus('')
    try {
      await api.clearRepositoryCache(repository.name)
      setStatus('Cache cleared.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to clear the cache')
    }
  }

  return (
    <Card className="mb-6 p-4">
      <form onSubmit={onSave} className="space-y-3">
        <div>
          <h2 className="text-sm font-medium text-neutral-200">Mirror</h2>
          <p className="mt-1 text-xs text-neutral-500">
            Requests this repository cannot answer are passed on to the upstream and cached. Released artifacts,
            layers and tarballs are kept indefinitely; the lifetime below only applies to what changes upstream —
            <code className="mx-1 text-neutral-400">maven-metadata.xml</code> and snapshots, packuments, and Docker
            tags.
          </p>
        </div>

        <UpstreamList type={repository.type} value={remotes} onChange={setRemotes} />

        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-neutral-400">
            Lifetime
            <Input
              type="number"
              min={0}
              value={ttl}
              onChange={(e) => setTtl(e.target.value)}
              className="max-w-24"
            />
            seconds
          </label>
          <Button type="submit">Save</Button>
          <Button type="button" variant="danger" onClick={onClear}>
            Clear cache
          </Button>
          {status && <p className="text-sm text-emerald-400">{status}</p>}
          <ErrorText>{error}</ErrorText>
        </div>
      </form>
    </Card>
  )
}

export function RepoPermissions({ repo }: { repo: string }) {
  const [permissions, setPermissions] = useState<RepositoryPermission[]>([])
  const [info, setInfo] = useState<Repository | null>(null)
  const [username, setUsername] = useState('')
  const [permission, setPermission] = useState<Permission>('READ')
  const [error, setError] = useState('')

  const reload = () => {
    api
      .permissions(repo)
      .then(setPermissions)
      .catch(() => setError('Failed to load permissions'))
    api
      .repositories()
      .then((all) => setInfo(all.find((entry) => entry.name === repo) ?? null))
      .catch(() => setError('Failed to load repository'))
  }

  useEffect(reload, [repo])

  const onGrant = async (event: FormEvent) => {
    event.preventDefault()
    if (!username.trim()) return
    setError('')
    try {
      await api.grant(repo, username.trim(), permission)
      setUsername('')
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to grant access')
    }
  }

  const onRevoke = async (user: string) => {
    await api.revoke(repo, user)
    reload()
  }

  return (
    <div>
      <button onClick={() => navigate('/admin')} className="mb-4 text-sm text-neutral-500 hover:text-neutral-300">
        ← Back to repositories
      </button>
      <PageHeading title={`Access · ${repo}`} subtitle="Grant users read or write access to this repository." />

      {info?.mode === 'PROXY' && <MirrorSettings repository={info} onSaved={reload} />}

      <Card className="mb-6 p-4">
        <form onSubmit={onGrant} className="flex flex-wrap items-center gap-3">
          <Input
            placeholder="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="max-w-xs"
          />
          <select
            value={permission}
            onChange={(e) => setPermission(e.target.value as Permission)}
            className="rounded-md border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-sm text-neutral-100 focus:outline-none"
          >
            <option value="READ">read</option>
            <option value="WRITE">write</option>
          </select>
          <Button type="submit">Grant</Button>
          <ErrorText>{error}</ErrorText>
        </form>
      </Card>

      <Table
        head={
          <tr>
            <Th>User</Th>
            <Th>Permission</Th>
            <Th className="text-right">Actions</Th>
          </tr>
        }
      >
        {permissions.length === 0 ? (
          <tr>
            <Td className="text-neutral-500">No users have been granted access.</Td>
            <Td />
            <Td />
          </tr>
        ) : (
          permissions.map((entry) => (
            <tr key={entry.username} className="hover:bg-neutral-900">
              <Td className="text-neutral-100">{entry.username}</Td>
              <Td>
                <PermissionBadge permission={entry.permission} />
              </Td>
              <Td className="text-right">
                <Button variant="danger" onClick={() => onRevoke(entry.username)}>
                  Revoke
                </Button>
              </Td>
            </tr>
          ))
        )}
      </Table>
    </div>
  )
}
