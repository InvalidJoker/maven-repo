import { CodeTabs } from './CodeTabs'

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
  const user = username ?? '<your-username>'

  return (
    <CodeTabs
      tabs={[
        { id: 'kts', label: 'Gradle Kotlin', code: gradleKts(repoUrl, user, coordinates) },
        { id: 'groovy', label: 'Gradle Groovy', code: gradleGroovy(repoUrl, user, coordinates) },
        { id: 'maven', label: 'Maven', code: maven(repoUrl, user, coordinates) },
      ]}
    />
  )
}
