import { navigate } from '../router'

export function AdminNav({ active }: { active: 'repositories' | 'users' }) {
  const tab = (label: string, to: string, key: 'repositories' | 'users') => (
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
    </div>
  )
}
