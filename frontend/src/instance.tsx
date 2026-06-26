import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, type Instance } from './api'

interface InstanceState extends Instance {
  refresh: () => Promise<void>
}

const InstanceContext = createContext<InstanceState>(null!)

export function InstanceProvider({ children }: { children: ReactNode }) {
  const [instance, setInstance] = useState<Instance>({ name: 'Maven Repository', iconUrl: null })

  const refresh = async () => {
    try {
      setInstance(await api.instance())
    } catch {
      // keep defaults if the instance endpoint is unreachable
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  // Keep the document title and favicon in sync with the configured branding.
  useEffect(() => {
    document.title = instance.name
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon']")
    if (!link) {
      link = document.createElement('link')
      link.rel = 'icon'
      document.head.appendChild(link)
    }
    link.href = instance.iconUrl ?? '/favicon.svg'
  }, [instance])

  return <InstanceContext.Provider value={{ ...instance, refresh }}>{children}</InstanceContext.Provider>
}

export function useInstance(): InstanceState {
  return useContext(InstanceContext)
}
