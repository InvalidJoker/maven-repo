import type { ReactNode } from 'react'
import { ChevronDown, ChevronUp, Plus, X } from 'lucide-react'
import type { FooterLink } from '../api'
import { Button, Input } from '../ui'
import { IconPicker } from './IconPicker'

export function FooterLinks({
  value,
  onChange,
}: {
  value: FooterLink[]
  onChange: (value: FooterLink[]) => void
}) {
  const update = (index: number, patch: Partial<FooterLink>) =>
    onChange(value.map((link, i) => (i === index ? { ...link, ...patch } : link)))

  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))

  const move = (index: number, delta: number) => {
    const moved = [...value]
    const [entry] = moved.splice(index, 1)
    moved.splice(index + delta, 0, entry)
    onChange(moved)
  }

  return (
    <div className="max-w-2xl space-y-2">
      {value.map((link, index) => (
        <div key={index} className="flex items-center gap-2">
          <IconPicker value={link.icon} onChange={(icon) => update(index, { icon })} />
          <Input
            placeholder="Documentation"
            value={link.label}
            maxLength={32}
            onChange={(e) => update(index, { label: e.target.value })}
            className="max-w-[12rem]"
          />
          <Input
            type="url"
            spellCheck={false}
            placeholder="https://docs.example.com"
            value={link.url}
            onChange={(e) => update(index, { url: e.target.value })}
          />
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
        </div>
      ))}

      <Button
        type="button"
        variant="ghost"
        disabled={value.length >= 10}
        onClick={() => onChange([...value, { label: '', url: '', icon: 'link' }])}
      >
        <Plus size={14} className="mr-1.5" />
        Add link
      </Button>
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
      className="shrink-0 rounded p-1 text-neutral-500 transition-colors hover:bg-neutral-800 hover:text-neutral-200 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-neutral-500"
    >
      {children}
    </button>
  )
}
