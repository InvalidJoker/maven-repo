import type {
  Permission,
  Repository,
  User,
  UserRepository,
  RepositoryPermission,
  Scope,
  Token,
  CreatedToken,
  BrowseResponse,
  BrowseEntry,
  SearchResult,
  Instance,
  AccentColor,
} from "./model";

export type {
  Permission,
  BrowseEntry,
  Repository,
  User,
  UserRepository,
  RepositoryPermission,
  Scope,
  Token,
  CreatedToken,
  BrowseResponse,
  SearchResult,
  Instance,
  AccentColor,
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type RequestMethod = "GET" | "POST" | "PUT" | "DELETE";

async function request<T>(
  method: RequestMethod,
  url: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(url, {
    method,
    headers:
      body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    credentials: "include",
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const data = await response.json();
      if (data?.error) message = data.error;
    } catch {
      // ignore non-JSON error bodies
    }
    throw new ApiError(response.status, message);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  me: () => request<User>("GET", "/auth/me"),
  login: (username: string, password: string) =>
    request<User>("POST", "/auth/login", { username, password }),
  logout: () => request<void>("POST", "/auth/logout"),

  visibleRepositories: () =>
    request<UserRepository[]>("GET", "/api/repositories"),
  browse: (repo: string, path: string) => {
    const encoded = path
      .split("/")
      .filter((segment) => segment.length > 0)
      .map(encodeURIComponent)
      .join("/");
    return request<BrowseResponse>(
      "GET",
      `/api/repositories/${encodeURIComponent(repo)}/tree/${encoded}`,
    );
  },
  search: (repo: string, query: string) =>
    request<SearchResult[]>(
      "GET",
      `/api/repositories/${encodeURIComponent(repo)}/search?q=${encodeURIComponent(query)}`,
    ),

  // tokens (current user)
  tokens: () => request<Token[]>("GET", "/api/tokens"),
  createToken: (name: string, scopes: Scope[]) =>
    request<CreatedToken>("POST", "/api/tokens", { name, scopes }),
  updateToken: (id: number, name: string, scopes: Scope[]) =>
    request<void>("PUT", `/api/tokens/${id}`, { name, scopes }),
  deleteToken: (id: number) => request<void>("DELETE", `/api/tokens/${id}`),

  // repositories (admin)
  repositories: () => request<Repository[]>("GET", "/api/repositories"),
  createRepository: (name: string, isPrivate: boolean) =>
    request<Repository>("POST", "/api/repositories", {
      name,
      private: isPrivate,
    }),
  permissions: (repo: string) =>
    request<RepositoryPermission[]>(
      "GET",
      `/api/repositories/${encodeURIComponent(repo)}/permissions`,
    ),
  grant: (repo: string, username: string, permission: Permission) =>
    request<void>(
      "POST",
      `/api/repositories/${encodeURIComponent(repo)}/permissions`,
      {
        username,
        permission,
      },
    ),
  revoke: (repo: string, username: string) =>
    request<void>(
      "DELETE",
      `/api/repositories/${encodeURIComponent(repo)}/permissions/${encodeURIComponent(username)}`,
    ),

  // instance branding
  instance: () => request<Instance>("GET", "/api/instance"),
  updateInstanceName: (name: string) =>
    request<Instance>("PUT", "/api/instance", { name }),
  setInstanceIconUrl: (url: string) =>
    request<Instance>("PUT", "/api/instance/icon", { url }),
  uploadInstanceIcon: async (file: File) => {
    const response = await fetch("/api/instance/icon", {
      method: "POST",
      body: file,
      headers: { "Content-Type": file.type },
      credentials: "include",
    });
    if (!response.ok) {
      let message = response.statusText;
      try {
        message = (await response.json()).error ?? message;
      } catch {
        // ignore
      }
      throw new ApiError(response.status, message);
    }
  },
  resetInstanceIcon: () => request<void>("DELETE", "/api/instance/icon"),
  setInstanceAccent: (accent: AccentColor) =>
    request<Instance>("PUT", "/api/instance/accent", { accent }),

  // users (admin)
  users: () => request<User[]>("GET", "/api/users"),
  createUser: (username: string, password: string, admin: boolean) =>
    request<User>("POST", "/api/users", { username, password, admin }),
  updateUser: (id: number, changes: { admin?: boolean; password?: string }) =>
    request<User>("PUT", `/api/users/${id}`, changes),
};
