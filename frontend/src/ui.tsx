import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import type { Permission } from './api'

type Variant = 'primary' | 'ghost' | 'danger'

const variants: Record<Variant, string> = {
  primary: 'bg-neutral-100 text-neutral-900 hover:bg-white border border-transparent',
  ghost: 'bg-transparent text-neutral-200 hover:bg-neutral-800 border border-neutral-700',
  danger: 'bg-transparent text-red-400 hover:bg-red-950/40 border border-red-900/60',
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
      className={`w-full rounded-md border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-sm text-neutral-100 placeholder:text-neutral-600 focus:border-neutral-500 focus:outline-none ${className}`}
    />
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-lg border border-neutral-800 bg-neutral-900/50 ${className}`}>
      {children}
    </div>
  )
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'green' | 'amber' }) {
  const tones = {
    neutral: 'border-neutral-700 text-neutral-400',
    green: 'border-emerald-800 text-emerald-400',
    amber: 'border-amber-800 text-amber-400',
  }
  return (
    <span className={`inline-flex items-center rounded border px-1.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

export function PermissionBadge({ permission }: { permission: Permission }) {
  return <Badge tone={permission === 'WRITE' ? 'amber' : 'neutral'}>{permission.toLowerCase()}</Badge>
}

export function VisibilityBadge({ isPrivate }: { isPrivate: boolean }) {
  return <Badge tone={isPrivate ? 'neutral' : 'green'}>{isPrivate ? 'private' : 'public'}</Badge>
}

export function ErrorText({ children }: { children: ReactNode }) {
  if (!children) return null
  return <p className="text-sm text-red-400">{children}</p>
}

export function PageHeading({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="mb-6">
      <h1 className="text-xl font-semibold text-neutral-100">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-neutral-500">{subtitle}</p>}
    </div>
  )
}
