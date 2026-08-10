import type { RepositoryType } from './api'

const NAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/

/**
 * Mirrors what the backend accepts. Docker repository names become the first segment of an image reference,
 * which the OCI spec keeps lowercase. Blank is "nothing typed yet", not an error.
 */
export function repositoryNameError(name: string, type: RepositoryType): string | null {
  const trimmed = name.trim()
  if (trimmed.length === 0) return null
  if (!NAME_PATTERN.test(trimmed)) return 'Letters, digits, dots, dashes and underscores only.'
  if (type === 'DOCKER' && trimmed !== trimmed.toLowerCase()) return 'Docker repository names must be lowercase.'
  return null
}
