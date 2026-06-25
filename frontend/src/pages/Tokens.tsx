import { useEffect, useState } from 'react'
import { api, type CreatedToken, type Permission, type Scope, type Token, type UserRepository } from '../api'
import { Badge, Button, Card, ErrorText, Input, PageHeading } from '../ui'

type ScopeChoice = 'none' | Permission

export function Tokens() {
  const [tokens, setTokens] = useState<Token[]>([])
  const [repos, setRepos] = useState<UserRepository[]>([])
  const [editing, setEditing] = useState<Token | 'new' | null>(null)
  const [created, setCreated] = useState<CreatedToken | null>(null)
  const [error, setError] = useState('')

  const reload = () => {
    Promise.all([api.tokens(), api.myRepositories()])
      .then(([t, r]) => {
        setTokens(t)
        setRepos(r)
      })
      .catch(() => setError('Failed to load tokens'))
  }

  useEffect(reload, [])

  const onDelete = async (token: Token) => {
    await api.deleteToken(token.id)
    reload()
  }

  return (
    <div>
      <div className="mb-6 flex items-start justify-between">
        <PageHeading title="Access tokens" subtitle="Use a token as the password for Gradle/Maven auth." />
        {editing === null && (
          <Button
            onClick={() => {
              setCreated(null)
              setEditing('new')
            }}
          >
            New token
          </Button>
        )}
      </div>

      <ErrorText>{error}</ErrorText>

      {created && (
        <Card className="mb-4 border-emerald-900/60 p-4">
          <p className="text-sm text-neutral-200">
            Copy your new token now — it won't be shown again.
          </p>
          <code className="mt-2 block break-all rounded-md border border-neutral-800 bg-neutral-950 p-3 text-xs text-emerald-300">
            {created.token}
          </code>
        </Card>
      )}

      {editing !== null && (
        <TokenForm
          token={editing === 'new' ? null : editing}
          repos={repos}
          onCancel={() => setEditing(null)}
          onSaved={(result) => {
            setEditing(null)
            setCreated(result)
            reload()
          }}
        />
      )}

      <div className="space-y-2">
        {tokens.map((token) => (
          <Card key={token.id} className="flex items-start justify-between p-4">
            <div>
              <div className="font-medium text-neutral-100">{token.name}</div>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {token.scopes.length === 0 ? (
                  <Badge>all repositories</Badge>
                ) : (
                  token.scopes.map((scope) => (
                    <Badge key={scope.repository}>
                      {scope.repository} · {scope.permission.toLowerCase()}
                    </Badge>
                  ))
                )}
              </div>
            </div>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => setEditing(token)}>
                Edit
              </Button>
              <Button variant="danger" onClick={() => onDelete(token)}>
                Delete
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}

function TokenForm({
  token,
  repos,
  onCancel,
  onSaved,
}: {
  token: Token | null
  repos: UserRepository[]
  onCancel: () => void
  onSaved: (created: CreatedToken | null) => void
}) {
  const [name, setName] = useState(token?.name ?? '')
  const [choices, setChoices] = useState<Record<string, ScopeChoice>>(() => {
    const initial: Record<string, ScopeChoice> = {}
    for (const scope of token?.scopes ?? []) initial[scope.repository] = scope.permission
    return initial
  })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const setChoice = (repo: string, choice: ScopeChoice) =>
    setChoices((prev) => ({ ...prev, [repo]: choice }))

  const onSubmit = async () => {
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    const scopes: Scope[] = Object.entries(choices)
      .filter(([, choice]) => choice !== 'none')
      .map(([repository, choice]) => ({ repository, permission: choice as Permission }))

    setBusy(true)
    setError('')
    try {
      if (token) {
        await api.updateToken(token.id, name.trim(), scopes)
        onSaved(null)
      } else {
        const created = await api.createToken(name.trim(), scopes)
        onSaved(created)
      }
    } catch {
      setError('Failed to save token')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="mb-4 p-4">
      <div className="space-y-4">
        <div>
          <label className="mb-1 block text-xs text-neutral-500">Name</label>
          <Input placeholder="e.g. ci-deploy" value={name} onChange={(e) => setName(e.target.value)} />
        </div>

        <div>
          <label className="mb-1 block text-xs text-neutral-500">
            Scopes — leave all on "none" to inherit your full access
          </label>
          <div className="divide-y divide-neutral-800 rounded-md border border-neutral-800">
            {repos.length === 0 && (
              <div className="p-3 text-sm text-neutral-500">No repositories available.</div>
            )}
            {repos.map((repo) => {
              const choice = choices[repo.name] ?? 'none'
              return (
                <div key={repo.name} className="flex items-center justify-between p-3">
                  <span className="text-sm text-neutral-200">{repo.name}</span>
                  <div className="flex gap-1">
                    {(['none', 'READ', 'WRITE'] as ScopeChoice[]).map((option) => (
                      <button
                        key={option}
                        onClick={() => setChoice(repo.name, option)}
                        className={`rounded px-2 py-1 text-xs transition-colors ${
                          choice === option
                            ? 'bg-neutral-100 text-neutral-900'
                            : 'text-neutral-400 hover:bg-neutral-800'
                        }`}
                      >
                        {option === 'none' ? 'none' : option.toLowerCase()}
                      </button>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <ErrorText>{error}</ErrorText>

        <div className="flex gap-2">
          <Button onClick={onSubmit} disabled={busy}>
            {token ? 'Save changes' : 'Create token'}
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </div>
    </Card>
  )
}
