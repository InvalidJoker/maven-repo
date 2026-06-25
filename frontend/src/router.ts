import { useEffect, useState } from 'react'

/** Current hash-route path, e.g. `/`, `/tokens`, `/admin/repos/releases`. */
export function useHashRoute(): string {
  const [path, setPath] = useState(currentPath)
  useEffect(() => {
    const onChange = () => setPath(currentPath())
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return path
}

export function navigate(path: string): void {
  window.location.hash = path
}

function currentPath(): string {
  const hash = window.location.hash.slice(1)
  return hash.length > 0 ? hash : '/'
}

/** Splits a path into non-empty, decoded segments. */
export function segments(path: string): string[] {
  return path
    .split('/')
    .filter((segment) => segment.length > 0)
    .map(decodeURIComponent)
}
