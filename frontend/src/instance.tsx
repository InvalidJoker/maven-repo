import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, type AccentColor, type Instance } from './api'
import { ACCENT_PALETTE } from './accents'

interface InstanceState extends Instance {
  refresh: () => Promise<void>
}

const InstanceContext = createContext<InstanceState>(null!)

function applyAccent(accent: AccentColor) {
  const [c300, c400, c500, c600] = ACCENT_PALETTE[accent] ?? ACCENT_PALETTE.EMERALD
  const root = document.documentElement
  root.style.setProperty('--color-brand-300', c300)
  root.style.setProperty('--color-brand-400', c400)
  root.style.setProperty('--color-brand-500', c500)
  root.style.setProperty('--color-brand-600', c600)
}

export function InstanceProvider({ children }: { children: ReactNode }) {
  const [instance, setInstance] = useState<Instance>({
    name: 'Maven Repository',
    iconUrl: null,
    accent: 'EMERALD',
    demo: false,
  })

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

  // Apply the configured accent palette.
  useEffect(() => {
    applyAccent(instance.accent)
  }, [instance.accent])

  return <InstanceContext.Provider value={{ ...instance, refresh }}>{children}</InstanceContext.Provider>
}

export function useInstance(): InstanceState {
  return useContext(InstanceContext)
}
