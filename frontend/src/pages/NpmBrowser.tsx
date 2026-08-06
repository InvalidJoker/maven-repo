import { useEffect, useState, type ReactNode } from "react";
import { Package, Search, Tag as TagIcon, Trash2 } from "lucide-react";
import {
  api,
  ApiError,
  type NpmPackage,
  type NpmPackageDetail,
  type NpmVersionDetail,
} from "../api";
import { formatDate, formatSize } from "../format";
import { navigate } from "../router";
import { Badge, Button, Card } from "../ui";
import { Breadcrumb, type Crumb } from "../components/Breadcrumb";
import { NpmSnippet } from "../components/NpmSnippet";

interface NpmBrowserProps {
  repo: string;
  pkg?: string;
  version?: string;
  canWrite: boolean;
}

function packageHref(repo: string, name: string, version?: string): string {
  const parts = [repo, name, ...(version ? [version] : [])];
  return "/repo/" + parts.map(encodeURIComponent).join("/");
}

export function NpmBrowser({ repo, pkg, version, canWrite }: NpmBrowserProps) {
  const registryUrl = `${window.location.origin}/npm/${repo}`;

  const crumbs: Crumb[] = [
    { label: "repositories", href: "/" },
    { label: repo, href: pkg ? `/repo/${encodeURIComponent(repo)}` : undefined },
    ...(pkg ? [{ label: pkg, href: version ? packageHref(repo, pkg) : undefined }] : []),
    ...(version ? [{ label: version }] : []),
  ];

  return (
    <div>
      <Breadcrumb items={crumbs} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_480px]">
        <div>
          {pkg === undefined ? (
            <PackageList repo={repo} />
          ) : version === undefined ? (
            <VersionList repo={repo} pkg={pkg} canWrite={canWrite} />
          ) : (
            <VersionDetail repo={repo} pkg={pkg} version={version} canWrite={canWrite} />
          )}
        </div>

        <aside className="space-y-4">
          <Card className="p-4">
            <PanelTitle>{pkg ? "Use this package" : "Publish a package"}</PanelTitle>
            <p className="mb-3 mt-1 text-xs text-neutral-500">
              {pkg
                ? "Point the scope at this registry, then install as usual."
                : "Configure the registry in .npmrc, then npm publish."}
            </p>
            <NpmSnippet
              registryUrl={registryUrl}
              packageName={pkg}
              version={version}
            />
            <p className="mt-3 text-xs text-neutral-500">
              The <span className="text-neutral-300">_authToken</span> is an access
              token from the Tokens page. Public repositories can be installed from
              without one.
            </p>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function PackageList({ repo }: { repo: string }) {
  const [packages, setPackages] = useState<NpmPackage[] | null>(null);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    api
      .npmPackages(repo)
      .then(setPackages)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load packages"),
      );
  }, [repo]);

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!packages) return null;

  if (packages.length === 0) {
    return (
      <Card className="p-4 text-sm text-neutral-500">
        No packages published to this repository yet.
      </Card>
    );
  }

  const needle = query.trim().toLowerCase();
  const visible = needle
    ? packages.filter((item) => item.name.toLowerCase().includes(needle))
    : packages;

  return (
    <div>
      <div className="relative mb-4">
        <Search
          size={16}
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-neutral-500"
        />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter packages…"
          className="w-full rounded-md border border-neutral-700 bg-neutral-900 py-1.5 pl-9 pr-3 text-sm text-neutral-100 placeholder:text-neutral-500 focus:border-brand-500 focus:ring-1 focus:ring-brand-500/40 focus:outline-none"
        />
      </div>

      <div className="overflow-hidden rounded-md border border-neutral-800">
        {visible.map((item) => (
          <button
            key={item.name}
            onClick={() => navigate(packageHref(repo, item.name))}
            className="flex w-full items-center gap-3 border-b border-neutral-800 px-3 py-2.5 text-left last:border-b-0 hover:bg-neutral-900"
          >
            <Package size={16} className="shrink-0 text-brand-400" />
            <div className="min-w-0">
              <div className="truncate text-sm font-medium text-neutral-100">
                {item.name}
              </div>
              <div className="truncate text-xs text-neutral-500">
                {item.description ?? `updated ${formatDate(item.modified)}`}
              </div>
            </div>
            <span className="ml-auto shrink-0">
              {item.latest && <Badge tone="amber">{item.latest}</Badge>}
            </span>
          </button>
        ))}
        {visible.length === 0 && (
          <div className="px-3 py-4 text-sm text-neutral-500">
            No packages match your filter.
          </div>
        )}
      </div>
    </div>
  );
}

function VersionList({
  repo,
  pkg,
  canWrite,
}: {
  repo: string;
  pkg: string;
  canWrite: boolean;
}) {
  const [detail, setDetail] = useState<NpmPackageDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .npmPackage(repo, pkg)
      .then(setDetail)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load package"),
      );
  }, [repo, pkg]);

  const onDeletePackage = async () => {
    setError("");
    try {
      await api.deleteNpmPackage(repo, pkg);
      navigate(`/repo/${encodeURIComponent(repo)}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete package");
    }
  };

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!detail) return null;

  return (
    <div>
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          {detail.description && (
            <p className="text-sm text-neutral-400">{detail.description}</p>
          )}
          <div className="mt-2 flex flex-wrap gap-1.5">
            {Object.entries(detail.distTags).map(([tag, target]) => (
              <Badge key={tag} tone="violet">
                {tag} · {target}
              </Badge>
            ))}
          </div>
        </div>
        {canWrite && (
          <Button variant="danger" onClick={onDeletePackage}>
            <Trash2 size={14} className="mr-1.5" />
            Delete package
          </Button>
        )}
      </div>

      <div className="overflow-hidden rounded-md border border-neutral-800">
        {detail.versions.map((item) => (
          <button
            key={item.version}
            onClick={() => navigate(packageHref(repo, pkg, item.version))}
            className="flex w-full items-center gap-3 border-b border-neutral-800 px-3 py-2.5 text-left last:border-b-0 hover:bg-neutral-900"
          >
            <TagIcon size={16} className="shrink-0 text-violet-400" />
            <span className="text-sm text-neutral-100">{item.version}</span>
            {item.tags.map((tag) => (
              <Badge key={tag}>{tag}</Badge>
            ))}
            <span className="ml-auto shrink-0 text-xs text-neutral-600">
              {formatDate(item.published)}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

function VersionDetail({
  repo,
  pkg,
  version,
  canWrite,
}: {
  repo: string;
  pkg: string;
  version: string;
  canWrite: boolean;
}) {
  const [detail, setDetail] = useState<NpmVersionDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setDetail(null);
    setError("");
    api
      .npmVersion(repo, pkg, version)
      .then(setDetail)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load version"),
      );
  }, [repo, pkg, version]);

  const onDelete = async () => {
    setError("");
    try {
      await api.deleteNpmVersion(repo, pkg, version);
      navigate(packageHref(repo, pkg));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete version");
    }
  };

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!detail) return null;

  const dependencies = Object.entries(detail.dependencies);
  const tarballUrl = `${window.location.origin}/npm/${repo}/${detail.name}/-/${detail.tarball}`;

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-neutral-100">
              {detail.name}@{detail.version}
            </h2>
            {detail.description && (
              <p className="mt-1 text-xs text-neutral-500">{detail.description}</p>
            )}
          </div>
          {canWrite && (
            <Button variant="danger" onClick={onDelete}>
              <Trash2 size={14} className="mr-1.5" />
              Delete version
            </Button>
          )}
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-3">
          {detail.tarballSize != null && (
            <Field label="Tarball">{formatSize(detail.tarballSize)}</Field>
          )}
          {detail.published && (
            <Field label="Published">{formatDate(detail.published)}</Field>
          )}
          {detail.license && <Field label="License">{detail.license}</Field>}
          {detail.tags.length > 0 && (
            <Field label="Tags">{detail.tags.join(", ")}</Field>
          )}
        </dl>

        {detail.integrity && (
          <code className="mt-3 block truncate text-xs text-neutral-600">
            {detail.integrity}
          </code>
        )}

        <a
          href={tarballUrl}
          className="mt-3 inline-block text-xs text-brand-400 hover:text-brand-300"
        >
          Download {detail.tarball}
        </a>
      </Card>

      {dependencies.length > 0 && (
        <Card className="p-4">
          <PanelTitle>Dependencies</PanelTitle>
          <dl className="mt-2 space-y-1 text-xs">
            {dependencies.map(([name, range]) => (
              <div key={name} className="flex gap-2">
                <dt className="shrink-0 text-neutral-400">{name}</dt>
                <dd className="truncate text-neutral-600">{range}</dd>
              </div>
            ))}
          </dl>
        </Card>
      )}

      {detail.keywords.length > 0 && (
        <Card className="p-4">
          <PanelTitle>Keywords</PanelTitle>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {detail.keywords.map((keyword) => (
              <Badge key={keyword}>{keyword}</Badge>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-neutral-500">{label}</dt>
      <dd className="text-neutral-200">{children}</dd>
    </div>
  );
}

function PanelTitle({ children }: { children: ReactNode }) {
  return <h2 className="text-sm font-semibold text-neutral-200">{children}</h2>;
}
