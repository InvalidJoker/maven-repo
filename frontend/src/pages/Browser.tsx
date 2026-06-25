import { useEffect, useState, type ReactNode } from 'react'
import { api, ApiError, type BrowseResponse } from '../api'
import { useAuth } from '../auth'
import { navigate } from '../router'
import { Badge, Card } from '../ui'
import { InstallSnippet } from '../components/InstallSnippet'

function pathSegments(path: string): string[] {
  return path.split('/').filter((segment) => segment.length > 0)
}

function repoHref(repo: string, segs: string[]): string {
  return '/repo/' + [repo, ...segs].map(encodeURIComponent).join('/')
}

function downloadUrl(repo: string, segs: string[], name: string): string {
  const encoded = [repo, ...segs, name].map(encodeURIComponent).join('/')
  return `${window.location.origin}/maven/${encoded}`
}

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function Browser({ repo, path }: { repo: string; path: string }) {
  const { user } = useAuth()
  const [data, setData] = useState<BrowseResponse | null>(null)
  const [error, setError] = useState('')
  const segs = pathSegments(path)

  useEffect(() => {
    setData(null)
    setError('')
    api
      .browse(repo, path)
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load'))
  }, [repo, path])

  const repoUrl = `${window.location.origin}/maven/${repo}`

  return (
    <div>
      <Breadcrumb repo={repo} segs={segs} />

      {error && <Card className="p-4 text-sm text-red-400">{error}</Card>}

      {data && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_480px]">
          <div>
            {segs.length > 0 && (
              <button
                onClick={() => navigate(repoHref(repo, segs.slice(0, -1)))}
                className="mb-2 block w-full rounded-md border border-neutral-800 px-3 py-2 text-left text-sm text-neutral-400 hover:bg-neutral-900"
              >
                ../
              </button>
            )}
            {data.entries.length === 0 && (
              <Card className="p-4 text-sm text-neutral-500">This directory is empty.</Card>
            )}
            <div className="overflow-hidden rounded-md border border-neutral-800">
              {data.entries.map((entry) =>
                entry.directory ? (
                  <button
                    key={entry.name}
                    onClick={() => navigate(repoHref(repo, [...segs, entry.name]))}
                    className="flex w-full items-center justify-between border-b border-neutral-800 px-3 py-2 text-left text-sm last:border-b-0 hover:bg-neutral-900"
                  >
                    <span className="text-neutral-200">{entry.name}/</span>
                  </button>
                ) : (
                  <a
                    key={entry.name}
                    href={downloadUrl(repo, segs, entry.name)}
                    className="flex items-center justify-between border-b border-neutral-800 px-3 py-2 text-sm last:border-b-0 hover:bg-neutral-900"
                  >
                    <span className="truncate text-neutral-400">{entry.name}</span>
                    <span className="ml-3 shrink-0 text-xs text-neutral-600">{formatSize(entry.size)}</span>
                  </a>
                ),
              )}
            </div>
          </div>

          <aside className="space-y-4">
            {data.version ? (
              <Card className="p-4">
                <PanelTitle>Install</PanelTitle>
                <p className="mb-3 text-xs text-neutral-500">
                  {data.version.groupId}:{data.version.artifactId}
                </p>
                <InstallSnippet repoUrl={repoUrl} username={user?.username} coordinates={data.version} />
              </Card>
            ) : data.artifact ? (
              <Card className="p-4">
                <div className="mb-3 flex items-center justify-between">
                  <PanelTitle>Install</PanelTitle>
                  <Badge tone="amber">latest · {data.artifact.latestVersion}</Badge>
                </div>
                <p className="mb-3 text-xs text-neutral-500">
                  {data.artifact.groupId}:{data.artifact.artifactId}
                </p>
                <InstallSnippet
                  repoUrl={repoUrl}
                  username={user?.username}
                  coordinates={{
                    groupId: data.artifact.groupId,
                    artifactId: data.artifact.artifactId,
                    version: data.artifact.latestVersion,
                  }}
                />
                <div className="mt-4">
                  <PanelTitle>Versions</PanelTitle>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {data.artifact.versions.map((v) => (
                      <button
                        key={v}
                        onClick={() => navigate(repoHref(repo, [...segs, v]))}
                        className="rounded border border-neutral-700 px-1.5 py-0.5 text-xs text-neutral-300 hover:bg-neutral-800"
                      >
                        {v}
                      </button>
                    ))}
                  </div>
                </div>
              </Card>
            ) : (
              <Card className="p-4">
                <PanelTitle>Repository</PanelTitle>
                <p className="mb-3 mt-1 text-xs text-neutral-500">Add this repository to your build.</p>
                <InstallSnippet repoUrl={repoUrl} username={user?.username} />
              </Card>
            )}
          </aside>
        </div>
      )}
    </div>
  )
}

function PanelTitle({ children }: { children: ReactNode }) {
  return <h2 className="text-sm font-semibold text-neutral-200">{children}</h2>
}

function Breadcrumb({ repo, segs }: { repo: string; segs: string[] }) {
  return (
    <div className="mb-6">
      <div className="flex flex-wrap items-center gap-1 text-sm">
        <button onClick={() => navigate('/')} className="text-neutral-500 hover:text-neutral-300">
          repositories
        </button>
        <span className="text-neutral-700">/</span>
        <button
          onClick={() => navigate(repoHref(repo, []))}
          className={segs.length === 0 ? 'text-neutral-100' : 'text-neutral-400 hover:text-neutral-200'}
        >
          {repo}
        </button>
        {segs.map((seg, index) => {
          const isLast = index === segs.length - 1
          return (
            <span key={index} className="flex items-center gap-1">
              <span className="text-neutral-700">/</span>
              <button
                onClick={() => navigate(repoHref(repo, segs.slice(0, index + 1)))}
                className={isLast ? 'text-neutral-100' : 'text-neutral-400 hover:text-neutral-200'}
              >
                {seg}
              </button>
            </span>
          )
        })}
      </div>
    </div>
  )
}
