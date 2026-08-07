import type { RepositoryType } from '../api'
import { UPSTREAMS, parseUpstreams } from '../upstreams'
import { Textarea } from '../ui'

export function UpstreamList({
  type,
  value,
  onChange,
}: {
  type: RepositoryType
  value: string
  onChange: (value: string) => void
}) {
  const append = (url: string) => {
    const urls = parseUpstreams(value)
    if (urls.includes(url)) return
    onChange([...urls, url].join('\n'))
  }

  return (
    <div className="space-y-2">
      <Textarea
        rows={Math.max(2, parseUpstreams(value).length + 1)}
        spellCheck={false}
        placeholder={'https://upstream-registry\nhttps://another-upstream'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="max-w-2xl"
      />
      <div className="flex flex-wrap items-center gap-1">
        {UPSTREAMS[type].map((preset) => (
          <button
            key={preset.url}
            type="button"
            onClick={() => append(preset.url)}
            className="rounded border border-neutral-700 px-2 py-0.5 text-xs text-neutral-400 transition-colors hover:border-neutral-600 hover:text-neutral-200"
          >
            + {preset.label}
          </button>
        ))}
        <span className="ml-1 text-xs text-neutral-500">
          One per line — the first upstream that has an artifact serves it.
        </span>
      </div>
    </div>
  )
}
