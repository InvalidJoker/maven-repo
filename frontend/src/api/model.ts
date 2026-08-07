export type Permission = "READ" | "WRITE";

export type RepositoryType = "MAVEN" | "DOCKER" | "NPM";

/** A repository either holds what was published to it, or mirrors an upstream registry on demand. */
export type RepositoryMode = "HOSTED" | "PROXY";

export interface User {
  id: number;
  username: string;
  admin: boolean;
}

export interface Repository {
  id: number;
  name: string;
  private: boolean;
  type: RepositoryType;
  mode: RepositoryMode;
  remoteUrl: string | null;
  cacheTtlSeconds: number;
}

export interface UserRepository {
  name: string;
  private: boolean;
  permission: Permission;
  type: RepositoryType;
  mode: RepositoryMode;
  remoteUrl: string | null;
  cacheTtlSeconds: number;
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

export interface DockerImage {
  name: string;
  tags: number;
  lastPushed: string | null;
}

export interface DockerTag {
  tag: string;
  digest: string | null;
  pushedAt: string | null;
}

export interface DockerLayer {
  digest: string;
  size: number;
  mediaType: string;
}

export interface DockerPlatform {
  digest: string;
  os: string | null;
  architecture: string | null;
  variant: string | null;
}

export interface DockerManifest {
  image: string;
  reference: string;
  digest: string;
  mediaType: string;
  manifestSize: number;
  totalSize: number;
  created: string | null;
  os: string | null;
  architecture: string | null;
  layers: DockerLayer[];
  platforms: DockerPlatform[];
  labels: Record<string, string>;
}

export interface NpmPackage {
  name: string;
  versions: number;
  latest: string | null;
  description: string | null;
  modified: string | null;
}

export interface NpmVersion {
  version: string;
  published: string | null;
  tags: string[];
}

export interface NpmPackageDetail {
  name: string;
  description: string | null;
  distTags: Record<string, string>;
  versions: NpmVersion[];
}

export interface NpmVersionDetail {
  name: string;
  version: string;
  description: string | null;
  license: string | null;
  homepage: string | null;
  published: string | null;
  tarball: string;
  tarballSize: number | null;
  shasum: string | null;
  integrity: string | null;
  tags: string[];
  keywords: string[];
  dependencies: Record<string, string>;
  devDependencies: Record<string, string>;
}

export type AccentColor = "EMERALD" | "INDIGO" | "BLUE" | "VIOLET" | "ROSE" | "AMBER";

export interface Instance {
  name: string;
  iconUrl: string | null;
  accent: AccentColor;
  demo: boolean;
  oidc: boolean;
  oidcLabel: string | null;
}
