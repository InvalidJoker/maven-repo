import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { api, ApiError } from '../api'
import { useInstance } from '../instance'
import { AdminNav } from '../components/AdminNav'
import { Button, Card, ErrorText, Input, PageHeading } from '../ui'

export function AdminConfig() {
  const { name: currentName, iconUrl, refresh } = useInstance()
  const [name, setName] = useState(currentName)
  const [logoUrl, setLogoUrl] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => setName(currentName), [currentName])

  const run = async (action: () => Promise<unknown>, failure: string) => {
    setError('')
    setBusy(true)
    try {
      await action()
      await refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : failure)
    } finally {
      setBusy(false)
    }
  }

  const onSaveName = (event: FormEvent) => {
    event.preventDefault()
    run(() => api.updateInstanceName(name.trim()), 'Failed to save name')
  }

  const onSetLogoUrl = (event: FormEvent) => {
    event.preventDefault()
    if (!logoUrl.trim()) return
    run(() => api.setInstanceIconUrl(logoUrl.trim()), 'Failed to set logo URL').then(() => setLogoUrl(''))
  }

  const onUploadIcon = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    run(() => api.uploadInstanceIcon(file), 'Failed to upload icon').finally(() => {
      if (fileRef.current) fileRef.current.value = ''
    })
  }

  const onResetIcon = () => run(() => api.resetInstanceIcon(), 'Failed to reset icon')

  return (
    <div>
      <AdminNav active="config" />
      <PageHeading title="Configuration" subtitle="Branding for this repository instance." />

      <Card className="mb-6 p-4">
        <h2 className="mb-3 text-sm font-semibold text-slate-200">Name</h2>
        <form onSubmit={onSaveName} className="flex flex-wrap items-center gap-3">
          <Input
            placeholder="Repository name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="max-w-xs"
          />
          <Button type="submit" disabled={busy}>
            Save
          </Button>
        </form>
        <p className="mt-2 text-xs text-slate-500">Shown in the header and the browser tab title.</p>
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 text-sm font-semibold text-slate-200">Logo</h2>
        <div className="flex items-start gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-700 bg-slate-900">
            {iconUrl ? (
              <img src={iconUrl} alt="logo" className="h-full w-full object-contain" />
            ) : (
              <span className="text-xs text-slate-600">none</span>
            )}
          </div>

          <div className="min-w-0 flex-1 space-y-4">
            <form onSubmit={onSetLogoUrl}>
              <label className="mb-1 block text-xs text-slate-500">Image URL</label>
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  type="url"
                  placeholder="https://avatars.githubusercontent.com/u/83445245"
                  value={logoUrl}
                  onChange={(e) => setLogoUrl(e.target.value)}
                  className="max-w-md"
                />
                <Button type="submit" disabled={busy}>
                  Use URL
                </Button>
              </div>
            </form>

            <div>
              <label className="mb-1 block text-xs text-slate-500">…or upload an image</label>
              <div className="flex flex-wrap items-center gap-2">
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/*"
                  onChange={onUploadIcon}
                  className="text-sm text-slate-400 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-500 file:px-3 file:py-1.5 file:text-sm file:text-white hover:file:bg-indigo-400"
                />
                {iconUrl && (
                  <Button variant="ghost" onClick={onResetIcon} disabled={busy}>
                    Reset to default
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Used as the app logo and browser favicon. Uploads are limited to 1&nbsp;MB.
        </p>
      </Card>

      <div className="mt-4">
        <ErrorText>{error}</ErrorText>
      </div>
    </div>
  )
}
