import { useState, type ReactNode } from 'react'
import { ChevronDown, ChevronUp, Plus, X } from 'lucide-react'
import type { RepositoryType } from '../api'
import { UPSTREAMS, normalizeUpstream } from '../upstreams'
import { Button, Input } from '../ui'

export function UpstreamList({
  type,
  value,
  onChange,
}: {
  type: RepositoryType
  value: string[]
  onChange: (value: string[]) => void
}) {
  const [draft, setDraft] = useState('')
  const [error, setError] = useState('')

  const append = (url: string) => {
    const normalized = normalizeUpstream(url)
    if (!normalized) {
      setError('Enter an http(s) URL, e.g. https://registry.example.com')
      return
    }
    setError('')
    setDraft('')
    if (value.includes(normalized)) return
    onChange([...value, normalized])
  }

  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))

  const move = (index: number, delta: number) => {
    const moved = [...value]
    const [entry] = moved.splice(index, 1)
    moved.splice(index + delta, 0, entry)
    onChange(moved)
  }

  return (
    <div className="max-w-2xl space-y-2">
      {value.length > 0 && (
        <ul className="divide-y divide-neutral-800 overflow-hidden rounded-md border border-neutral-800">
          {value.map((url, index) => (
            <li key={url} className="flex items-center gap-2 bg-neutral-900/40 px-2 py-1.5">
              <span className="w-5 shrink-0 text-center text-xs text-neutral-600">{index + 1}</span>
              <span className="min-w-0 flex-1 truncate font-mono text-sm text-neutral-200">{url}</span>
              <IconButton label="Move up" disabled={index === 0} onClick={() => move(index, -1)}>
                <ChevronUp size={14} />
              </IconButton>
              <IconButton
                label="Move down"
                disabled={index === value.length - 1}
                onClick={() => move(index, 1)}
              >
                <ChevronDown size={14} />
              </IconButton>
              <IconButton label="Remove" onClick={() => remove(index)}>
                <X size={14} />
              </IconButton>
            </li>
          ))}
        </ul>
      )}

      <div className="flex items-center gap-2">
        <Input
          spellCheck={false}
          placeholder="https://registry.example.com"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value)
            setError('')
          }}
          // The editor lives inside a form, where Enter would otherwise submit it.
          onKeyDown={(e) => {
            if (e.key !== 'Enter') return
            e.preventDefault()
            append(draft)
          }}
        />
        <Button type="button" variant="ghost" disabled={draft.trim().length === 0} onClick={() => append(draft)}>
          <Plus size={14} />
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-1">
        {UPSTREAMS[type]
          .filter((preset) => !value.includes(preset.url))
          .map((preset) => (
            <button
              key={preset.url}
              type="button"
              onClick={() => append(preset.url)}
              className="rounded border border-neutral-700 px-2 py-0.5 text-xs text-neutral-400 transition-colors hover:border-neutral-600 hover:text-neutral-200"
            >
              + {preset.label}
            </button>
          ))}
      </div>

      <p className={`text-xs ${error ? 'text-rose-400' : 'text-neutral-500'}`}>
        {error || 'Consulted top to bottom — the first upstream that has an artifact serves it.'}
      </p>
    </div>
  )
}

function IconButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string
  disabled?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="rounded p-1 text-neutral-500 transition-colors hover:bg-neutral-800 hover:text-neutral-200 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-neutral-500"
    >
      {children}
    </button>
  )
}
