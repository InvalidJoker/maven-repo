import { useEffect, useState, type ReactNode } from 'react'
import {
  File as FileIcon,
  FileArchive,
  FileCode,
  Folder,
  Hash,
  Package,
  Search,
} from 'lucide-react'
import { api, ApiError, type BrowseEntry, type BrowseResponse, type SearchResult } from '../api'
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

function EntryIcon({ entry }: { entry: BrowseEntry }) {
  if (entry.directory) return <Folder className="h-4 w-4 shrink-0 text-indigo-400" />
  const name = entry.name.toLowerCase()
  if (/\.(jar|war|aar|zip|tar|gz|module)$/.test(name))
    return <FileArchive className="h-4 w-4 shrink-0 text-amber-400" />
  if (/\.(pom|xml)$/.test(name)) return <FileCode className="h-4 w-4 shrink-0 text-sky-400" />
  if (/\.(md5|sha1|sha256|sha512|asc)$/.test(name))
    return <Hash className="h-4 w-4 shrink-0 text-slate-500" />
  return <FileIcon className="h-4 w-4 shrink-0 text-slate-400" />
}

export function Browser({ repo, path }: { repo: string; path: string }) {
  const { user } = useAuth()
  const [data, setData] = useState<BrowseResponse | null>(null)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const segs = pathSegments(path)

  useEffect(() => {
    setData(null)
    setError('')
    api
      .browse(repo, path)
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load'))
  }, [repo, path])

  // Debounced package search across the whole repository.
  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) {
      setResults(null)
      return
    }
    const handle = setTimeout(() => {
      api
        .search(repo, q)
        .then(setResults)
        .catch(() => setResults([]))
    }, 250)
    return () => clearTimeout(handle)
  }, [repo, query])

  const repoUrl = `${window.location.origin}/maven/${repo}`

  return (
    <div>
      <Breadcrumb repo={repo} segs={segs} />

      <div className="relative mb-4">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search packages in this repository…"
          className="w-full rounded-md border border-slate-700 bg-slate-900 py-1.5 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/40 focus:outline-none"
        />
      </div>

      {error && <Card className="p-4 text-sm text-rose-400">{error}</Card>}

      {results !== null ? (
        <SearchResults
          results={results}
          onSelect={(target) => {
            setQuery('')
            navigate(repoHref(repo, target.split('/')))
          }}
        />
      ) : (
        data && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_480px]">
            <div>
              {segs.length > 0 && (
                <button
                  onClick={() => navigate(repoHref(repo, segs.slice(0, -1)))}
                  className="mb-2 block w-full rounded-md border border-slate-800 px-3 py-2 text-left text-sm text-slate-400 hover:bg-slate-900"
                >
                  ../
                </button>
              )}
              {data.entries.length === 0 && (
                <Card className="p-4 text-sm text-slate-500">This directory is empty.</Card>
              )}
              <div className="overflow-hidden rounded-md border border-slate-800">
                {data.entries.map((entry) =>
                  entry.directory ? (
                    <button
                      key={entry.name}
                      onClick={() => navigate(repoHref(repo, [...segs, entry.name]))}
                      className="flex w-full items-center gap-2 border-b border-slate-800 px-3 py-2 text-left text-sm last:border-b-0 hover:bg-slate-900"
                    >
                      <EntryIcon entry={entry} />
                      <span className="text-slate-200">{entry.name}/</span>
                    </button>
                  ) : (
                    <a
                      key={entry.name}
                      href={downloadUrl(repo, segs, entry.name)}
                      className="flex items-center gap-2 border-b border-slate-800 px-3 py-2 text-sm last:border-b-0 hover:bg-slate-900"
                    >
                      <EntryIcon entry={entry} />
                      <span className="truncate text-slate-400">{entry.name}</span>
                      <span className="ml-auto shrink-0 text-xs text-slate-600">{formatSize(entry.size)}</span>
                    </a>
                  ),
                )}
              </div>
            </div>

            <aside className="space-y-4">
              {data.version ? (
                <Card className="p-4">
                  <PanelTitle>Install</PanelTitle>
                  <p className="mb-3 text-xs text-slate-500">
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
                  <p className="mb-3 text-xs text-slate-500">
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
                          className="rounded border border-slate-700 px-1.5 py-0.5 text-xs text-slate-300 hover:border-indigo-600 hover:text-indigo-300"
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
                  <p className="mb-3 mt-1 text-xs text-slate-500">Add this repository to your build.</p>
                  <InstallSnippet repoUrl={repoUrl} username={user?.username} />
                </Card>
              )}
            </aside>
          </div>
        )
      )}
    </div>
  )
}

function SearchResults({
  results,
  onSelect,
}: {
  results: SearchResult[]
  onSelect: (path: string) => void
}) {
  if (results.length === 0) {
    return <Card className="p-4 text-sm text-slate-500">No packages match your search.</Card>
  }
  return (
    <div className="overflow-hidden rounded-md border border-slate-800">
      {results.map((result) => (
        <button
          key={result.path}
          onClick={() => onSelect(result.path)}
          className="flex w-full items-center gap-3 border-b border-slate-800 px-3 py-2.5 text-left last:border-b-0 hover:bg-slate-900"
        >
          <Package className="h-4 w-4 shrink-0 text-indigo-400" />
          <div className="min-w-0">
            <div className="truncate text-sm text-slate-100">
              {result.groupId}:<span className="font-medium">{result.artifactId}</span>
            </div>
            <div className="truncate text-xs text-slate-500">{result.path}</div>
          </div>
          <Badge tone="amber">{result.latestVersion}</Badge>
        </button>
      ))}
    </div>
  )
}

function PanelTitle({ children }: { children: ReactNode }) {
  return <h2 className="text-sm font-semibold text-slate-200">{children}</h2>
}

function Breadcrumb({ repo, segs }: { repo: string; segs: string[] }) {
  return (
    <div className="mb-6">
      <div className="flex flex-wrap items-center gap-1 text-sm">
        <button onClick={() => navigate('/')} className="text-slate-500 hover:text-slate-300">
          repositories
        </button>
        <span className="text-slate-700">/</span>
        <button
          onClick={() => navigate(repoHref(repo, []))}
          className={segs.length === 0 ? 'text-slate-100' : 'text-slate-400 hover:text-slate-200'}
        >
          {repo}
        </button>
        {segs.map((seg, index) => {
          const isLast = index === segs.length - 1
          return (
            <span key={index} className="flex items-center gap-1">
              <span className="text-slate-700">/</span>
              <button
                onClick={() => navigate(repoHref(repo, segs.slice(0, index + 1)))}
                className={isLast ? 'text-slate-100' : 'text-slate-400 hover:text-slate-200'}
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
