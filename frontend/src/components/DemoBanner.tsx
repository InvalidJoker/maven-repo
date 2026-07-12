import { TriangleAlert } from "lucide-react";

export function DemoBanner() {
  return (
    <div className="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 bg-amber-500 px-4 py-2 text-center text-sm font-medium text-neutral-950">
      <TriangleAlert size={16} className="shrink-0" />
      <span>
        Demo instance — this software is self-hosted and there is no public
        repository. Deploy your own:
      </span>
      <a
        href="https://github.com/InvalidJoker/maven-repo"
        target="_blank"
        rel="noreferrer"
        className="font-semibold underline underline-offset-2"
      >
        github.com/InvalidJoker/maven-repo
      </a>
    </div>
  );
}
