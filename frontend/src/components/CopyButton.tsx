import { useState } from 'react'
import { Check, Copy } from 'lucide-react'

export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard may be unavailable (e.g. insecure context)
    }
  }

  return (
    <button
      type="button"
      onClick={onCopy}
      aria-label="Copy to clipboard"
      className="absolute right-2 top-2 rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-800 hover:text-slate-200"
    >
      <span className="relative block h-4 w-4">
        <Copy
          className={`absolute inset-0 h-4 w-4 transition-all duration-200 ease-out ${
            copied ? 'scale-0 opacity-0' : 'scale-100 opacity-100'
          }`}
        />
        <Check
          className={`absolute inset-0 h-4 w-4 text-emerald-400 transition-all duration-200 ease-out ${
            copied ? 'scale-100 opacity-100' : 'scale-0 opacity-0'
          }`}
        />
      </span>
    </button>
  )
}
