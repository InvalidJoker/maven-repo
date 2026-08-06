import { navigate } from '../router'

export interface Crumb {
  label: string
  /** Omitted for the current location, which is rendered as plain text. */
  href?: string
}

export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <div className="mb-6 flex flex-wrap items-center gap-1 text-sm">
      {items.map((crumb, index) => (
        <span key={index} className="flex items-center gap-1">
          {index > 0 && <span className="text-neutral-700">/</span>}
          {crumb.href ? (
            <button
              onClick={() => navigate(crumb.href!)}
              className="text-neutral-400 hover:text-neutral-200"
            >
              {crumb.label}
            </button>
          ) : (
            <span className="text-neutral-100">{crumb.label}</span>
          )}
        </span>
      ))}
    </div>
  )
}
