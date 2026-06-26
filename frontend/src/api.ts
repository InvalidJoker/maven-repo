export type Permission = 'READ' | 'WRITE'

export interface User {
  id: number
  username: string
  admin: boolean
}

export interface Repository {
  id: number
  name: string
  private: boolean
}

export interface UserRepository {
  name: string
  private: boolean
  permission: Permission
}

export interface RepositoryPermission {
  username: string
  permission: Permission
}

export interface Scope {
  repository: string
  permission: Permission
}

export interface Token {
  id: number
  name: string
  scopes: Scope[]
}

export interface CreatedToken {
  token: string
  info: Token
}

export interface BrowseEntry {
  name: string
  directory: boolean
  size: number | null
}

export interface ArtifactInfo {
  groupId: string
  artifactId: string
  versions: string[]
  latestVersion: string
}

export interface VersionInfo {
  groupId: string
  artifactId: string
  version: string
}

export interface BrowseResponse {
  repository: string
  path: string
  entries: BrowseEntry[]
  artifact: ArtifactInfo | null
  version: VersionInfo | null
}

export interface SearchResult {
  path: string
  groupId: string
  artifactId: string
  latestVersion: string
}

export interface Instance {
  name: string
  iconUrl: string | null
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    credentials: 'include',
  })

  if (!response.ok) {
    let message = response.statusText
    try {
      const data = await response.json()
      if (data?.error) message = data.error
    } catch {
      // ignore non-JSON error bodies
    }
    throw new ApiError(response.status, message)
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  // auth
  me: () => request<User>('GET', '/auth/me'),
  login: (username: string, password: string) =>
    request<User>('POST', '/auth/login', { username, password }),
  logout: () => request<void>('POST', '/auth/logout'),

  // repositories visible to the caller (public ones for anonymous visitors)
  visibleRepositories: () => request<UserRepository[]>('GET', '/api/repositories'),
  browse: (repo: string, path: string) => {
    const encoded = path
      .split('/')
      .filter((segment) => segment.length > 0)
      .map(encodeURIComponent)
      .join('/')
    return request<BrowseResponse>(
      'GET',
      `/api/repositories/${encodeURIComponent(repo)}/tree/${encoded}`,
    )
  },
  search: (repo: string, query: string) =>
    request<SearchResult[]>(
      'GET',
      `/api/repositories/${encodeURIComponent(repo)}/search?q=${encodeURIComponent(query)}`,
    ),

  // tokens (current user)
  tokens: () => request<Token[]>('GET', '/api/tokens'),
  createToken: (name: string, scopes: Scope[]) =>
    request<CreatedToken>('POST', '/api/tokens', { name, scopes }),
  updateToken: (id: number, name: string, scopes: Scope[]) =>
    request<void>('PUT', `/api/tokens/${id}`, { name, scopes }),
  deleteToken: (id: number) => request<void>('DELETE', `/api/tokens/${id}`),

  // repositories (admin)
  repositories: () => request<Repository[]>('GET', '/api/repositories'),
  createRepository: (name: string, isPrivate: boolean) =>
    request<Repository>('POST', '/api/repositories', { name, private: isPrivate }),
  permissions: (repo: string) =>
    request<RepositoryPermission[]>('GET', `/api/repositories/${encodeURIComponent(repo)}/permissions`),
  grant: (repo: string, username: string, permission: Permission) =>
    request<void>('POST', `/api/repositories/${encodeURIComponent(repo)}/permissions`, {
      username,
      permission,
    }),
  revoke: (repo: string, username: string) =>
    request<void>(
      'DELETE',
      `/api/repositories/${encodeURIComponent(repo)}/permissions/${encodeURIComponent(username)}`,
    ),

  // instance branding
  instance: () => request<Instance>('GET', '/api/instance'),
  updateInstanceName: (name: string) => request<Instance>('PUT', '/api/instance', { name }),
  setInstanceIconUrl: (url: string) => request<Instance>('PUT', '/api/instance/icon', { url }),
  uploadInstanceIcon: async (file: File) => {
    const response = await fetch('/api/instance/icon', {
      method: 'POST',
      body: file,
      headers: { 'Content-Type': file.type },
      credentials: 'include',
    })
    if (!response.ok) {
      let message = response.statusText
      try {
        message = (await response.json()).error ?? message
      } catch {
        // ignore
      }
      throw new ApiError(response.status, message)
    }
  },
  resetInstanceIcon: () => request<void>('DELETE', '/api/instance/icon'),

  // users (admin)
  users: () => request<User[]>('GET', '/api/users'),
  createUser: (username: string, password: string, admin: boolean) =>
    request<User>('POST', '/api/users', { username, password, admin }),
  updateUser: (id: number, changes: { admin?: boolean; password?: string }) =>
    request<User>('PUT', `/api/users/${id}`, changes),
}
