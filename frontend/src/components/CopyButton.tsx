import { useState } from 'react'

// Lucide icons (https://lucide.dev), inlined to avoid a runtime dependency.
function CopyIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
      <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 6 9 17l-4-4" />
    </svg>
  )
}

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
      className="absolute right-2 top-2 rounded-md p-1.5 text-neutral-500 transition-colors hover:bg-neutral-800 hover:text-neutral-200"
    >
      <span className="relative block h-4 w-4">
        <span
          className={`absolute inset-0 transition-all duration-200 ease-out ${
            copied ? 'scale-0 opacity-0' : 'scale-100 opacity-100'
          }`}
        >
          <CopyIcon />
        </span>
        <span
          className={`absolute inset-0 text-emerald-400 transition-all duration-200 ease-out ${
            copied ? 'scale-100 opacity-100' : 'scale-0 opacity-0'
          }`}
        >
          <CheckIcon />
        </span>
      </span>
    </button>
  )
}
