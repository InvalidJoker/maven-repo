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

  if (info.type === "DOCKER") {
    return (
      <DockerBrowser
        repo={repo}
        image={parts[0]}
        tag={parts[1]}
        canWrite={info.permission === "WRITE"}
      />
    );
  }

  if (info.type === "NPM") {
    return (
      <NpmBrowser
        repo={repo}
        pkg={parts[0]}
        version={parts[1]}
        canWrite={info.permission === "WRITE"}
      />
    );
  }

  return <MavenBrowser repo={repo} path={parts.join("/")} />;
}
