export type Permission = "READ" | "WRITE";

export interface User {
  id: number;
  username: string;
  admin: boolean;
}

export interface Repository {
  id: number;
  name: string;
  private: boolean;
}

export interface UserRepository {
  name: string;
  private: boolean;
  permission: Permission;
}

export interface RepositoryPermission {
  username: string;
  permission: Permission;
}

export interface Scope {
  repository: string;
  permission: Permission;
}

export interface Token {
  id: number;
  name: string;
  scopes: Scope[];
}

export interface CreatedToken {
  token: string;
  info: Token;
}

export interface BrowseEntry {
  name: string;
  directory: boolean;
  size: number | null;
  kind: "PACKAGE" | "VERSION" | "FOLDER" | "FILE";
}

export interface ArtifactInfo {
  groupId: string;
  artifactId: string;
  versions: string[];
  latestVersion: string;
}

export interface VersionInfo {
  groupId: string;
  artifactId: string;
  version: string;
}

export interface BrowseResponse {
  repository: string;
  path: string;
  entries: BrowseEntry[];
  artifact: ArtifactInfo | null;
  version: VersionInfo | null;
}

export interface SearchResult {
  path: string;
  groupId: string;
  artifactId: string;
  latestVersion: string;
}

export type AccentColor = "EMERALD" | "INDIGO" | "BLUE" | "VIOLET" | "ROSE" | "AMBER";

export interface Instance {
  name: string;
  iconUrl: string | null;
  accent: AccentColor;
  demo: boolean;
}
