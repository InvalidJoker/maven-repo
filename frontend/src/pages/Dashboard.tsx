import { useEffect, useState } from 'react'
import { api, type UserRepository } from '../api'
import { Card, PageHeading, PermissionBadge, VisibilityBadge } from '../ui'

function repoUrl(name: string): string {
  return `${window.location.origin}/maven/${name}`
}

export function Dashboard() {
  const [repos, setRepos] = useState<UserRepository[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .visibleRepositories()
      .then(setRepos)
      .catch(() => setError('Failed to load repositories'))
  }, [])

  return (
    <div>
      <PageHeading title="Repositories" subtitle="Browse the repositories available to you." />

      {error && <p className="text-sm text-red-400">{error}</p>}

      {repos && repos.length === 0 && (
        <Card className="p-6 text-sm text-neutral-500">No repositories available yet.</Card>
      )}

      <div className="space-y-2">
        {repos?.map((repo) => (
          <Card key={repo.name} className="flex items-center justify-between p-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-medium text-neutral-100">{repo.name}</span>
                <VisibilityBadge isPrivate={repo.private} />
                <PermissionBadge permission={repo.permission} />
              </div>
              <code className="mt-1 block truncate text-xs text-neutral-500">{repoUrl(repo.name)}</code>
            </div>
          </Card>
        ))}
      </div>

      <Card className="mt-8 p-5">
        <h2 className="mb-2 text-sm font-semibold text-neutral-200">Using a repository in Gradle</h2>
        <pre className="overflow-x-auto rounded-md border border-neutral-800 bg-neutral-950 p-4 text-xs leading-relaxed text-neutral-300">
{`maven {
    url = uri("${window.location.origin}/maven/<repository>")
    credentials {
        username = "<your-username>"
        password = "<access-token>"
    }
}`}
        </pre>
        <p className="mt-2 text-xs text-neutral-500">
          Public repositories can be read without credentials. Create an access token under{' '}
          <span className="text-neutral-300">Tokens</span> to publish or read private repositories.
        </p>
      </Card>
    </div>
  )
}
