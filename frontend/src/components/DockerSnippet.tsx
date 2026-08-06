import { CodeTabs } from './CodeTabs'

interface DockerSnippetProps {
  /** Registry host as the Docker CLI addresses it, e.g. `registry.example.com`. */
  host: string
  repository: string
  username?: string
  image?: string
  tag?: string
}

export function DockerSnippet({ host, repository, username, image, tag }: DockerSnippetProps) {
  const user = username ?? '<your-username>'
  const reference = `${host}/${repository}/${image ?? '<image>'}:${tag ?? '<tag>'}`

  return (
    <CodeTabs
      tabs={[
        { id: 'pull', label: 'Pull', code: `docker pull ${reference}` },
        {
          id: 'push',
          label: 'Push',
          code: [
            `docker tag <local-image> ${reference}`,
            `docker push ${reference}`,
          ].join('\n'),
        },
        {
          id: 'login',
          label: 'Login',
          code: `docker login ${host} -u ${user} -p <access-token>`,
        },
      ]}
    />
  )
}
