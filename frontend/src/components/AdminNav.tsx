import { navigate } from '../router'

type AdminTab = 'repositories' | 'users' | 'config'

export function AdminNav({ active }: { active: AdminTab }) {
  const tab = (label: string, to: string, key: AdminTab) => (
    <button
      onClick={() => navigate(to)}
      className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
        active === key ? 'bg-indigo-500/15 text-indigo-300' : 'text-slate-400 hover:text-slate-100'
      }`}
    >
      {label}
    </button>
  )
  return (
    <div className="mb-6 flex gap-1 border-b border-slate-800 pb-3">
      {tab('Repositories', '/admin', 'repositories')}
      {tab('Users', '/admin/users', 'users')}
      {tab('Configuration', '/admin/config', 'config')}
    </div>
  )
}
