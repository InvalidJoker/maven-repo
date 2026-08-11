import { useEffect, useRef, useState } from 'react'
import { ICON_NAMES, ICONS, icon } from '../icons'
import { Input } from '../ui'

/** Button showing the selected lucide icon; opens a searchable grid of the icons we ship. */
export function IconPicker({ value, onChange }: { value: string; onChange: (name: string) => void }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const ref = useRef<HTMLDivElement>(null)
  const Selected = icon(value)

  useEffect(() => {
    if (!open) return
    const onDown = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const matches = ICON_NAMES.filter((name) => name.includes(query.trim().toLowerCase()))

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        title={value}
        aria-label={`Icon: ${value}`}
        onClick={() => {
          setQuery('')
          setOpen(!open)
        }}
        className="flex h-[34px] w-[34px] items-center justify-center rounded-md border border-neutral-700 bg-neutral-900 text-neutral-300 transition-colors hover:border-neutral-600 hover:text-neutral-100"
      >
        <Selected size={16} />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-20 mt-1 w-72 rounded-md border border-neutral-700 bg-neutral-950 p-2 shadow-xl shadow-black/60">
          <Input
            autoFocus
            placeholder="Search icons"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return
              e.preventDefault()
              if (matches[0]) {
                onChange(matches[0])
                setOpen(false)
              }
            }}
          />
          <div className="mt-2 grid max-h-56 grid-cols-8 gap-1 overflow-y-auto">
            {matches.map((name) => {
              const Icon = ICONS[name]
              return (
                <button
                  key={name}
                  type="button"
                  title={name}
                  aria-label={name}
                  onClick={() => {
                    onChange(name)
                    setOpen(false)
                  }}
                  className={`flex h-8 items-center justify-center rounded transition-colors ${
                    name === value
                      ? 'bg-brand-500/20 text-brand-300'
                      : 'text-neutral-400 hover:bg-neutral-800 hover:text-neutral-100'
                  }`}
                >
                  <Icon size={16} />
                </button>
              )
            })}
          </div>
          {matches.length === 0 && (
            <p className="px-1 py-2 text-xs text-neutral-500">No icon matches “{query}”.</p>
          )}
        </div>
      )}
    </div>
  )
}
