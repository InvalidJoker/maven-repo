import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import type { Permission } from './api'

type Variant = 'primary' | 'ghost' | 'danger'

const variants: Record<Variant, string> = {
  primary: 'bg-indigo-500 text-white hover:bg-indigo-400 border border-transparent',
  ghost: 'bg-transparent text-slate-200 hover:bg-slate-800 border border-slate-700',
  danger: 'bg-transparent text-rose-400 hover:bg-rose-950/40 border border-rose-900/60',
}

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
    />
  )
}

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/40 focus:outline-none ${className}`}
    />
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-lg border border-slate-800 bg-slate-900/60 ${className}`}>
      {children}
    </div>
  )
}

type Tone = 'neutral' | 'green' | 'amber' | 'sky' | 'violet'

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: Tone }) {
  const tones: Record<Tone, string> = {
    neutral: 'border-slate-700 bg-slate-800/40 text-slate-300',
    green: 'border-emerald-800 bg-emerald-950/40 text-emerald-300',
    amber: 'border-amber-800 bg-amber-950/40 text-amber-300',
    sky: 'border-sky-800 bg-sky-950/40 text-sky-300',
    violet: 'border-violet-800 bg-violet-950/40 text-violet-300',
  }
  return (
    <span className={`inline-flex items-center rounded border px-1.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

export function PermissionBadge({ permission }: { permission: Permission }) {
  return <Badge tone={permission === 'WRITE' ? 'amber' : 'sky'}>{permission.toLowerCase()}</Badge>
}

export function VisibilityBadge({ isPrivate }: { isPrivate: boolean }) {
  return <Badge tone={isPrivate ? 'violet' : 'green'}>{isPrivate ? 'private' : 'public'}</Badge>
}

export function ErrorText({ children }: { children: ReactNode }) {
  if (!children) return null
  return <p className="text-sm text-red-400">{children}</p>
}

export function PageHeading({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="mb-6">
      <h1 className="text-xl font-semibold text-slate-100">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
    </div>
  )
}
