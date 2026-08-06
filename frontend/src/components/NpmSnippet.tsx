import { CodeTabs } from './CodeTabs'

interface NpmSnippetProps {
  /** Registry URL of the repository, e.g. `https://forge.example.com/npm/packages`. */
  registryUrl: string
  packageName?: string
  version?: string
}

/** The `.npmrc` host key npm matches against: the registry URL without its scheme. */
function authKey(registryUrl: string): string {
  return registryUrl.replace(/^https?:/, '')
}

export function NpmSnippet({ registryUrl, packageName, version }: NpmSnippetProps) {
  const name = packageName ?? '<package>'
  const scope = packageName?.startsWith('@') ? packageName.split('/')[0] : '<@scope>'
  const spec = version ? `${name}@${version}` : name

  return (
    <CodeTabs
      tabs={[
        { id: 'install', label: 'Install', code: `npm install ${spec}` },
        {
          id: 'npmrc',
          label: '.npmrc',
          code: [
            `${scope}:registry=${registryUrl}/`,
            `${authKey(registryUrl)}/:_authToken=<access-token>`,
          ].join('\n'),
        },
        {
          id: 'publish',
          label: 'Publish',
          code: [`npm publish --registry ${registryUrl}/`].join('\n'),
        },
      ]}
    />
  )
}
