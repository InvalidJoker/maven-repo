import { useEffect, useState } from "react";
import { api, type UserRepository } from "../api";
import { useAuth } from "../auth";
import { navigate } from "../router";
import {
  Card,
  PageHeading,
  PermissionBadge,
  TypeBadge,
  VisibilityBadge,
} from "../ui";
import { DockerSnippet } from "../components/DockerSnippet";
import { InstallSnippet } from "../components/InstallSnippet";

function endpoint(repo: UserRepository): string {
  return repo.type === "DOCKER"
    ? `${window.location.host}/${repo.name}/<image>`
    : `${window.location.origin}/maven/${repo.name}`;
}

export function Dashboard() {
  const { user } = useAuth();
  const [repos, setRepos] = useState<UserRepository[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .visibleRepositories()
      .then(setRepos)
      .catch(() => setError("Failed to load repositories"));
  }, []);

  const hasDocker = repos?.some((repo) => repo.type === "DOCKER") ?? false;

  return (
    <div>
      <PageHeading
        title="Repositories"
        subtitle="Browse the Maven and Docker repositories available to you."
      />

      {error && <p className="text-sm text-red-400">{error}</p>}

      {repos && repos.length === 0 && (
        <Card className="p-6 text-sm text-neutral-500">
          No repositories available yet.
        </Card>
      )}

      <div className="space-y-2">
        {repos?.map((repo) => (
          <button
            key={repo.name}
            onClick={() => navigate(`/repo/${encodeURIComponent(repo.name)}`)}
            className="block w-full text-left"
          >
            <Card className="flex items-center justify-between p-4 transition-colors hover:border-neutral-700 hover:bg-neutral-900">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-neutral-100">
                    {repo.name}
                  </span>
                  <TypeBadge type={repo.type} />
                  {user ? (
                    <>
                      <VisibilityBadge isPrivate={repo.private} />
                      <PermissionBadge permission={repo.permission} />
                    </>
                  ) : null}
                </div>
                <code className="mt-1 block truncate text-xs text-neutral-500">
                  {endpoint(repo)}
                </code>
              </div>
              <span className="ml-3 shrink-0 text-neutral-600">→</span>
            </Card>
          </button>
        ))}
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <h2 className="mb-3 text-sm font-semibold text-neutral-200">
            Using a Maven repository in your build
          </h2>
          <InstallSnippet
            repoUrl={`${window.location.origin}/maven/<repository>`}
            username={user?.username}
          />
        </Card>

        {hasDocker && (
          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-neutral-200">
              Using a Docker repository
            </h2>
            <DockerSnippet
              host={window.location.host}
              repository="<repository>"
              username={user?.username}
            />
          </Card>
        )}
      </div>

      <p className="mt-3 text-xs text-neutral-500">
        Public repositories can be read without credentials. Create an access
        token under <span className="text-neutral-300">Tokens</span> to publish
        or read private repositories — the same token works for Gradle/Maven and{" "}
        <span className="text-neutral-300">docker login</span>.
      </p>
    </div>
  );
}
