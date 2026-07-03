import { useEffect, useState, type FormEvent } from 'react'
import { api, type Permission, type RepositoryPermission } from '../api'
import { navigate } from '../router'
import { Button, Card, ErrorText, Input, PageHeading, PermissionBadge, Table, Td, Th } from '../ui'

export function RepoPermissions({ repo }: { repo: string }) {
  const [permissions, setPermissions] = useState<RepositoryPermission[]>([])
  const [username, setUsername] = useState('')
  const [permission, setPermission] = useState<Permission>('READ')
  const [error, setError] = useState('')

  const reload = () => {
    api
      .permissions(repo)
      .then(setPermissions)
      .catch(() => setError('Failed to load permissions'))
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
