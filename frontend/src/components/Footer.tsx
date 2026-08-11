import { Github } from "lucide-react";

export function Footer() {
  return (
    <footer className="mx-auto flex w-full max-w-6xl items-center justify-center border-t border-neutral-800 px-4 py-4">
      <a
        href="https://github.com/InvalidJoker/artifact-forge"
        target="_blank"
        rel="noreferrer"
        className="flex items-center gap-2 text-sm text-neutral-500 transition-colors hover:text-neutral-300"
      >
        <Github size={16} />
        InvalidJoker/artifact-forge
      </a>
    </footer>
  );
}
