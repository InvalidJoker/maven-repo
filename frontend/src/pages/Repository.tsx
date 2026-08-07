import { useEffect, useState } from "react";
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
      {info.mode === "PROXY" && <MirrorNotice remoteUrl={info.remoteUrl} />}
      {browser}
    </div>
  );
}

/** A mirror only shows what has been pulled through it, which is worth saying before the listing looks empty. */
function MirrorNotice({ remoteUrl }: { remoteUrl: string | null }) {
  return (
    <Card className="mb-4 p-3 text-xs text-neutral-400">
      Mirror of <span className="text-neutral-200">{remoteUrl}</span>. Listed below is what has been requested
      through it so far — anything else is fetched from the upstream on first use.
    </Card>
  );
}
