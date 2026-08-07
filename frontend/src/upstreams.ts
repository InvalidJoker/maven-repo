import type { RepositoryType } from './api'

/** Upstreams are edited as text, one per line, in the order a mirror consults them. */
export function parseUpstreams(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

/** Registries people usually mirror, offered as one-click presets when creating a mirror repository. */
export const UPSTREAMS: Record<RepositoryType, { label: string; url: string }[]> = {
  MAVEN: [
    { label: 'Maven Central', url: 'https://repo1.maven.org/maven2' },
    { label: 'Google', url: 'https://dl.google.com/dl/android/maven2' },
    { label: 'Gradle Plugins', url: 'https://plugins.gradle.org/m2' },
    { label: 'Central Snapshots', url: 'https://central.sonatype.com/repository/maven-snapshots' },
    { label: 'JitPack', url: 'https://jitpack.io' },
  ],
  DOCKER: [
    { label: 'Docker Hub', url: 'https://registry-1.docker.io' },
    { label: 'GitHub', url: 'https://ghcr.io' },
    { label: 'Quay', url: 'https://quay.io' },
    { label: 'Kubernetes', url: 'https://registry.k8s.io' },
  ],
  NPM: [
    { label: 'npmjs', url: 'https://registry.npmjs.org' },
    { label: 'Yarn', url: 'https://registry.yarnpkg.com' },
  ],
}
