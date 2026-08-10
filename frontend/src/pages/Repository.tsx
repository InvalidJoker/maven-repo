import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, Cloud } from "lucide-react";
import { api, ApiError, type UserRepository } from "../api";
import { Card } from "../ui";
import { DockerBrowser } from "./DockerBrowser";
import { MavenBrowser } from "./MavenBrowser";
import { NpmBrowser } from "./NpmBrowser";

/**
 * A repository URL (`/#/repo/<name>/…`) does not say which format it holds, so the metadata is fetched first and
 * the matching browser takes over. The remaining segments are `<image>`/`<tag>` for Docker and
 * `<package>`/`<version>` for npm; both are one URI-encoded segment, so `team/api` and `@scope/pkg` stay intact.
 */
export function Repository({ repo, parts }: { repo: string; parts: string[] }) {
  const [info, setInfo] = useState<UserRepository | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setInfo(null);
    setError("");
    api
      .repository(repo)
      .then(setInfo)
      .catch((err) =>
        setError(
          err instanceof ApiError ? err.message : "Failed to load repository",
        ),
      );
  }, [repo]);

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!info) return null;

  const canWrite = info.permission === "WRITE";
  const browser =
    info.type === "DOCKER" ? (
      <DockerBrowser repo={repo} image={parts[0]} tag={parts[1]} canWrite={canWrite} />
    ) : info.type === "NPM" ? (
      <NpmBrowser repo={repo} pkg={parts[0]} version={parts[1]} canWrite={canWrite} />
    ) : (
      <MavenBrowser repo={repo} path={parts.join("/")} />
    );

  return (
    <div>
      {info.mode === "PROXY" && <MirrorNotice remoteUrls={info.remoteUrls} />}
      {browser}
    </div>
  );
}

/**
 * A mirror only lists what has been pulled through it, which is worth saying before the listing looks empty —
 * but it is context, not the page, so it stays a single line until someone asks for the upstreams.
 */
function MirrorNotice({ remoteUrls }: { remoteUrls: string[] }) {
  const [open, setOpen] = useState(false);

  return (
    <Card className="mb-4">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-neutral-400 transition-colors hover:text-neutral-200"
      >
        {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <Cloud size={14} className="text-amber-400" />
        <span>
          Mirror of{" "}
          <span className="text-neutral-200">
            {remoteUrls.length} {remoteUrls.length === 1 ? "upstream" : "upstreams"}
          </span>
        </span>
      </button>

      {open && (
        <div className="border-t border-neutral-800 px-3 py-2.5">
          <ol className="space-y-1">
            {remoteUrls.map((url, index) => (
              <li key={url} className="flex gap-2 text-xs">
                <span className="w-4 shrink-0 text-right text-neutral-600">{index + 1}</span>
                <code className="truncate text-neutral-300">{url}</code>
              </li>
            ))}
          </ol>
          <p className="mt-2.5 text-xs text-neutral-500">
            Listed below is what has been requested through this repository so far. Anything else is fetched on
            first use, from the first upstream that has it.
          </p>
        </div>
      )}
    </Card>
  );
}
