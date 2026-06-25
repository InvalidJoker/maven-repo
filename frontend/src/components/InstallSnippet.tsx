import { useState } from 'react'
import { CopyButton } from './CopyButton'

type Tab = 'kts' | 'groovy' | 'maven'

export interface Coordinates {
  groupId: string
  artifactId: string
  version: string
}

interface InstallSnippetProps {
  repoUrl: string
  username?: string
  coordinates?: Coordinates
}

const TABS: { id: Tab; label: string }[] = [
  { id: 'kts', label: 'Gradle Kotlin' },
  { id: 'groovy', label: 'Gradle Groovy' },
  { id: 'maven', label: 'Maven' },
]

function gradleKts(repoUrl: string, user: string, c?: Coordinates): string {
  const repo = `repositories {
    maven {
        url = uri("${repoUrl}")
        credentials {
            username = "${user}"
            password = "<access-token>"
        }
    }
}`
  const dep = c
    ? `

dependencies {
    implementation("${c.groupId}:${c.artifactId}:${c.version}")
}`
    : ''
  return repo + dep
}

function gradleGroovy(repoUrl: string, user: string, c?: Coordinates): string {
  const repo = `repositories {
    maven {
        url '${repoUrl}'
        credentials {
            username '${user}'
            password '<access-token>'
        }
    }
}`
  const dep = c
    ? `

dependencies {
    implementation '${c.groupId}:${c.artifactId}:${c.version}'
}`
    : ''
  return repo + dep
}

function maven(repoUrl: string, user: string, c?: Coordinates): string {
  const repo = `<repositories>
    <repository>
        <id>maven-repo</id>
        <url>${repoUrl}</url>
    </repository>
</repositories>`
  const dep = c
    ? `

<dependencies>
    <dependency>
        <groupId>${c.groupId}</groupId>
        <artifactId>${c.artifactId}</artifactId>
        <version>${c.version}</version>
    </dependency>
</dependencies>`
    : ''
  const settings = `

<!-- ~/.m2/settings.xml -->
<servers>
    <server>
        <id>maven-repo</id>
        <username>${user}</username>
        <password>&lt;access-token&gt;</password>
    </server>
</servers>`
  return repo + dep + settings
}

export function InstallSnippet({ repoUrl, username, coordinates }: InstallSnippetProps) {
  const [tab, setTab] = useState<Tab>('kts')
  const user = username ?? '<your-username>'

  const code =
    tab === 'kts'
      ? gradleKts(repoUrl, user, coordinates)
      : tab === 'groovy'
        ? gradleGroovy(repoUrl, user, coordinates)
        : maven(repoUrl, user, coordinates)

  return (
    <div>
      <div className="mb-2 flex gap-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`rounded px-2 py-1 text-xs transition-colors ${
              tab === t.id ? 'bg-indigo-500 text-white' : 'text-slate-400 hover:bg-slate-800'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="relative">
        <CopyButton text={code} />
        <pre className="overflow-x-auto rounded-md border border-slate-800 bg-slate-950 p-4 pr-12 text-xs leading-relaxed text-slate-300">
          {code}
        </pre>
      </div>
    </div>
  )
}
