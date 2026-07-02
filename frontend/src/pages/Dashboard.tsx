import { useEffect, useState } from "react";
import { api, type UserRepository } from "../api";
import { useAuth } from "../auth";
import { navigate } from "../router";
import { Card, PageHeading, PermissionBadge, VisibilityBadge } from "../ui";
import { InstallSnippet } from "../components/InstallSnippet";

function repoUrl(name: string): string {
  return `${window.location.origin}/maven/${name}`;
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

  return (
    <div>
      <PageHeading
        title="Repositories"
        subtitle="Browse the repositories available to you."
      />

      {error && <p className="text-sm text-red-400">{error}</p>}

      {repos && repos.length === 0 && (
        <Card className="p-6 text-sm text-slate-500">
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
            <Card className="flex items-center justify-between p-4 transition-colors hover:border-slate-700 hover:bg-slate-900">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-100">
                    {repo.name}
                  </span>
                  {user ? (
                    <>
                      <VisibilityBadge isPrivate={repo.private} />
                      <PermissionBadge permission={repo.permission} />
                    </>
                  ) : null}
                </div>
                <code className="mt-1 block truncate text-xs text-slate-500">
                  {repoUrl(repo.name)}
                </code>
              </div>
              <span className="ml-3 shrink-0 text-slate-600">→</span>
            </Card>
          </button>
        ))}
      </div>

      <Card className="mt-8 p-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-200">
          Using a repository in your build
        </h2>
        <InstallSnippet
          repoUrl={`${window.location.origin}/maven/<repository>`}
          username={user?.username}
        />
        <p className="mt-3 text-xs text-slate-500">
          Public repositories can be read without credentials. Create an access
          token under <span className="text-slate-300">Tokens</span> to publish
          or read private repositories.
        </p>
      </Card>
    </div>
  );
}
