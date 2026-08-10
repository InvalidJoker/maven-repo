import { useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { api, type Repository } from '../api'
import { navigate } from '../router'
import { AdminNav } from '../components/AdminNav'
import { CreateRepositoryDialog } from '../components/CreateRepositoryDialog'
import { Button, ErrorText, ModeBadge, PageHeading, Table, Td, Th, TypeBadge, VisibilityBadge } from '../ui'

export function Admin() {
  const [repos, setRepos] = useState<Repository[]>([])
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')

  const reload = () => {
    api.repositories().then(setRepos).catch(() => setError('Failed to load repositories'))
  }

  useEffect(reload, [])

  return (
    <div>
      <AdminNav active="repositories" />

      {/* PageHeading brings its own bottom margin, so the row does not add another. */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHeading title="Repositories" subtitle="Create repositories and manage who can access them." />
        <Button onClick={() => setCreating(true)} className="gap-1.5">
          <Plus size={14} />
          New repository
        </Button>
      </div>

      <ErrorText>{error}</ErrorText>

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
            <Td className="text-neutral-500">No repositories yet.</Td>
            <Td />
            <Td />
            <Td />
          </tr>
        ) : (
          repos.map((repo) => (
            <tr key={repo.name} className="hover:bg-neutral-900">
              <Td className="font-medium text-neutral-100">
                {repo.name}
                {repo.remoteUrls?.map((url) => (
                  <span key={url} className="mt-0.5 block truncate text-xs font-normal text-neutral-500">
                    → {url}
                  </span>
                ))}
              </Td>
              <Td>
                <div className="flex items-center gap-1.5">
                  <TypeBadge type={repo.type} />
                  <ModeBadge mode={repo.mode} />
                </div>
              </Td>
              <Td>
                <VisibilityBadge isPrivate={repo.private} />
              </Td>
              <Td className="text-right">
                <Button variant="ghost" onClick={() => navigate(`/admin/repos/${encodeURIComponent(repo.name)}`)}>
                  Manage
                </Button>
              </Td>
            </tr>
          ))
        )}
      </Table>

      {creating && <CreateRepositoryDialog onClose={() => setCreating(false)} onCreated={reload} />}
    </div>
  )
}
