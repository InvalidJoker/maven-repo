import type { AccentColor } from './api'

export const ACCENT_PALETTE: Record<AccentColor, [string, string, string, string]> = {
  EMERALD: ['#6ee7b7', '#3ecf8e', '#24b47e', '#1c9d6a'],
  INDIGO: ['#a5b4fc', '#818cf8', '#6366f1', '#4f46e5'],
  BLUE: ['#93c5fd', '#60a5fa', '#3b82f6', '#2563eb'],
  VIOLET: ['#c4b5fd', '#a78bfa', '#8b5cf6', '#7c3aed'],
  ROSE: ['#fda4af', '#fb7185', '#f43f5e', '#e11d48'],
  AMBER: ['#fcd34d', '#fbbf24', '#f59e0b', '#d97706'],
}

export const ACCENT_OPTIONS = Object.keys(ACCENT_PALETTE) as AccentColor[]
