import { useState } from 'react'

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
  const [copied, setCopied] = useState(false)
  const user = username ?? '<your-username>'

  const code =
    tab === 'kts'
      ? gradleKts(repoUrl, user, coordinates)
      : tab === 'groovy'
        ? gradleGroovy(repoUrl, user, coordinates)
        : maven(repoUrl, user, coordinates)

  const onCopy = async () => {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <div className="flex gap-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`rounded px-2 py-1 text-xs transition-colors ${
                tab === t.id ? 'bg-neutral-100 text-neutral-900' : 'text-neutral-400 hover:bg-neutral-800'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <button onClick={onCopy} className="text-xs text-neutral-500 hover:text-neutral-300">
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre className="overflow-x-auto rounded-md border border-neutral-800 bg-neutral-950 p-4 text-xs leading-relaxed text-neutral-300">
        {code}
      </pre>
    </div>
  )
}
