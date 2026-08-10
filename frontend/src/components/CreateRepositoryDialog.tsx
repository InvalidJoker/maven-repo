import { useState, type FormEvent, type ReactNode } from 'react'
import { api, type RepositoryMode, type RepositoryType } from '../api'
import { repositoryNameError } from '../repository'
import { Button, ErrorText, Input } from '../ui'
import { Modal } from './Modal'
import { UpstreamList } from './UpstreamList'

const TYPES: { id: RepositoryType; label: string; hint: string }[] = [
  { id: 'MAVEN', label: 'Maven', hint: '/maven/<name> — Gradle, Maven' },
  { id: 'DOCKER', label: 'Docker', hint: '/v2 — docker, podman, buildx' },
  { id: 'NPM', label: 'npm', hint: '/npm/<name> — npm, pnpm, yarn, bun' },
]

const MODES: { id: RepositoryMode; label: string; hint: string }[] = [
  { id: 'HOSTED', label: 'Hosted', hint: 'Holds what is published to it.' },
  { id: 'PROXY', label: 'Mirror', hint: 'Read-only. Serves upstream registries and caches what it hands out.' },
]

export function CreateRepositoryDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState('')
  const [type, setType] = useState<RepositoryType>('MAVEN')
  const [mode, setMode] = useState<RepositoryMode>('HOSTED')
  const [isPrivate, setPrivate] = useState(false)
  const [upstreams, setUpstreams] = useState<string[]>([])
  const [ttl, setTtl] = useState('600')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const nameError = repositoryNameError(name, type)
  const ready = name.trim().length > 0 && !nameError && (mode === 'HOSTED' || upstreams.length > 0)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!ready) return
    setBusy(true)
    setError('')
    try {
      await api.createRepository({
        name: name.trim(),
        private: isPrivate,
        type,
        mode,
        remoteUrls: mode === 'PROXY' ? upstreams : undefined,
        cacheTtlSeconds: mode === 'PROXY' ? Number(ttl) : undefined,
      })
      onCreated()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create repository')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title="New repository"
      subtitle="Pick the format clients will speak and whether it holds its own artifacts or mirrors someone else's."
      onClose={onClose}
      footer={
        <>
          <ErrorText>{error}</ErrorText>
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="create-repository" disabled={busy || !ready}>
            {busy ? 'Creating…' : 'Create'}
          </Button>
        </>
      }
    >
      <form id="create-repository" onSubmit={onSubmit} className="space-y-5">
        <Field label="Name">
          <Input
            autoFocus
            placeholder="repository-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="max-w-xs"
          />
          {nameError && <Hint tone="error">{nameError}</Hint>}
        </Field>

        <Field label="Format">
          <div className="grid gap-2 sm:grid-cols-3">
            {TYPES.map((option) => (
              <Choice
                key={option.id}
                active={type === option.id}
                label={option.label}
                hint={option.hint}
                onClick={() => setType(option.id)}
              />
            ))}
          </div>
        </Field>

        <Field label="Contents">
          <div className="grid gap-2 sm:grid-cols-2">
            {MODES.map((option) => (
              <Choice
                key={option.id}
                active={mode === option.id}
                label={option.label}
                hint={option.hint}
                onClick={() => setMode(option.id)}
              />
            ))}
          </div>
        </Field>

        {mode === 'PROXY' && (
          <>
            <Field label="Upstreams">
              <UpstreamList type={type} value={upstreams} onChange={setUpstreams} />
            </Field>

            <Field label="Cache">
              <label className="flex flex-wrap items-center gap-2 text-sm text-neutral-400">
                Refetch changing metadata after
                <Input
                  type="number"
                  min={0}
                  value={ttl}
                  onChange={(e) => setTtl(e.target.value)}
                  className="max-w-24"
                />
                seconds
              </label>
              <Hint>Released artifacts, layers and tarballs are cached for good; only metadata expires.</Hint>
            </Field>
          </>
        )}

        <Field label="Access">
          <label className="flex items-center gap-2 text-sm text-neutral-300">
            <input
              type="checkbox"
              checked={isPrivate}
              onChange={(e) => setPrivate(e.target.checked)}
              className="accent-brand-500"
            />
            Private — only users you grant access to can read it
          </label>
        </Field>
      </form>
    </Modal>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1.5 sm:grid-cols-[7rem_1fr] sm:gap-4">
      <span className="pt-1.5 text-sm text-neutral-400">{label}</span>
      <div className="min-w-0 space-y-1.5">{children}</div>
    </div>
  )
}

function Choice({
  active,
  label,
  hint,
  onClick,
}: {
  active: boolean
  label: string
  hint: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg border px-3 py-2 text-left transition-colors ${
        active
          ? 'border-brand-500 bg-brand-500/10'
          : 'border-neutral-800 bg-neutral-900/40 hover:border-neutral-700 hover:bg-neutral-900'
      }`}
    >
      <span className={`block text-sm font-medium ${active ? 'text-brand-300' : 'text-neutral-200'}`}>{label}</span>
      <span className="mt-0.5 block text-xs text-neutral-500">{hint}</span>
    </button>
  )
}

function Hint({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'error' }) {
  return (
    <p className={`text-xs ${tone === 'error' ? 'text-rose-400' : 'text-neutral-500'}`}>{children}</p>
  )
}
