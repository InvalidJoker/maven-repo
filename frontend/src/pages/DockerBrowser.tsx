import { useEffect, useState, type ReactNode } from "react";
import { Box, Layers, Search, Tag as TagIcon, Trash2 } from "lucide-react";
import {
  api,
  ApiError,
  type DockerImage,
  type DockerManifest,
  type DockerTag,
} from "../api";
import { useAuth } from "../auth";
import { formatDate, formatSize, shortDigest } from "../format";
import { navigate } from "../router";
import { Badge, Button, Card } from "../ui";
import { Breadcrumb, type Crumb } from "../components/Breadcrumb";
import { DockerSnippet } from "../components/DockerSnippet";

interface DockerBrowserProps {
  repo: string;
  image?: string;
  tag?: string;
  canWrite: boolean;
}

function imageHref(repo: string, image: string, tag?: string): string {
  const parts = [repo, image, ...(tag ? [tag] : [])];
  return "/repo/" + parts.map(encodeURIComponent).join("/");
}

export function DockerBrowser({ repo, image, tag, canWrite }: DockerBrowserProps) {
  const { user } = useAuth();
  const host = window.location.host;

  const crumbs: Crumb[] = [
    { label: "repositories", href: "/" },
    { label: repo, href: image ? `/repo/${encodeURIComponent(repo)}` : undefined },
    ...(image ? [{ label: image, href: tag ? imageHref(repo, image) : undefined }] : []),
    ...(tag ? [{ label: tag }] : []),
  ];

  return (
    <div>
      <Breadcrumb items={crumbs} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_480px]">
        <div>
          {image === undefined ? (
            <ImageList repo={repo} />
          ) : tag === undefined ? (
            <TagList repo={repo} image={image} canWrite={canWrite} />
          ) : (
            <TagDetail repo={repo} image={image} tag={tag} canWrite={canWrite} />
          )}
        </div>

        <aside className="space-y-4">
          <Card className="p-4">
            <PanelTitle>{image ? "Use this image" : "Push an image"}</PanelTitle>
            <p className="mb-3 mt-1 text-xs text-neutral-500">
              {image
                ? "Authenticate once, then pull by tag."
                : "Images are named <repository>/<image>. Any image you push creates itself."}
            </p>
            <DockerSnippet
              host={host}
              repository={repo}
              username={user?.username}
              image={image}
              tag={tag}
            />
            <p className="mt-3 text-xs text-neutral-500">
              Use an access token as the password. Docker requires HTTPS unless the
              registry is listed under{" "}
              <span className="text-neutral-300">insecure-registries</span>.
            </p>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function ImageList({ repo }: { repo: string }) {
  const [images, setImages] = useState<DockerImage[] | null>(null);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    api
      .dockerImages(repo)
      .then(setImages)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load images"),
      );
  }, [repo]);

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!images) return null;

  const needle = query.trim().toLowerCase();
  const visible = needle
    ? images.filter((item) => item.name.toLowerCase().includes(needle))
    : images;

  if (images.length === 0) {
    return (
      <Card className="p-4 text-sm text-neutral-500">
        No images pushed to this repository yet.
      </Card>
    );
  }

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
          placeholder="Filter images…"
          className="w-full rounded-md border border-neutral-700 bg-neutral-900 py-1.5 pl-9 pr-3 text-sm text-neutral-100 placeholder:text-neutral-500 focus:border-brand-500 focus:ring-1 focus:ring-brand-500/40 focus:outline-none"
        />
      </div>

      <div className="overflow-hidden rounded-md border border-neutral-800">
        {visible.map((item) => (
          <button
            key={item.name}
            onClick={() => navigate(imageHref(repo, item.name))}
            className="flex w-full items-center gap-3 border-b border-neutral-800 px-3 py-2.5 text-left last:border-b-0 hover:bg-neutral-900"
          >
            <Box size={16} className="shrink-0 text-brand-400" />
            <div className="min-w-0">
              <div className="truncate text-sm font-medium text-neutral-100">
                {item.name}
              </div>
              {item.lastPushed && (
                <div className="truncate text-xs text-neutral-500">
                  updated {formatDate(item.lastPushed)}
                </div>
              )}
            </div>
            <span className="ml-auto shrink-0">
              <Badge tone="sky">
                {item.tags} {item.tags === 1 ? "tag" : "tags"}
              </Badge>
            </span>
          </button>
        ))}
        {visible.length === 0 && (
          <div className="px-3 py-4 text-sm text-neutral-500">
            No images match your filter.
          </div>
        )}
      </div>
    </div>
  );
}

function TagList({
  repo,
  image,
  canWrite,
}: {
  repo: string;
  image: string;
  canWrite: boolean;
}) {
  const [tags, setTags] = useState<DockerTag[] | null>(null);
  const [error, setError] = useState("");

  const reload = () => {
    api
      .dockerTags(repo, image)
      .then(setTags)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load tags"),
      );
  };

  useEffect(reload, [repo, image]);

  const onDeleteImage = async () => {
    setError("");
    try {
      await api.deleteDockerImage(repo, image);
      navigate(`/repo/${encodeURIComponent(repo)}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete image");
    }
  };

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!tags) return null;

  return (
    <div>
      {canWrite && (
        <div className="mb-3 flex justify-end">
          <Button variant="danger" onClick={onDeleteImage}>
            <Trash2 size={14} className="mr-1.5" />
            Delete image
          </Button>
        </div>
      )}

      {tags.length === 0 ? (
        <Card className="p-4 text-sm text-neutral-500">This image has no tags.</Card>
      ) : (
        <div className="overflow-hidden rounded-md border border-neutral-800">
          {tags.map((item) => (
            <button
              key={item.tag}
              onClick={() => navigate(imageHref(repo, image, item.tag))}
              className="flex w-full items-center gap-3 border-b border-neutral-800 px-3 py-2.5 text-left last:border-b-0 hover:bg-neutral-900"
            >
              <TagIcon size={16} className="shrink-0 text-violet-400" />
              <div className="min-w-0">
                <div className="truncate text-sm text-neutral-100">{item.tag}</div>
                <div className="truncate font-mono text-xs text-neutral-500">
                  {shortDigest(item.digest)}
                </div>
              </div>
              <span className="ml-auto shrink-0 text-xs text-neutral-600">
                {formatDate(item.pushedAt)}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function TagDetail({
  repo,
  image,
  tag,
  canWrite,
}: {
  repo: string;
  image: string;
  tag: string;
  canWrite: boolean;
}) {
  const [manifest, setManifest] = useState<DockerManifest | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setManifest(null);
    setError("");
    api
      .dockerManifest(repo, image, tag)
      .then(setManifest)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Failed to load manifest"),
      );
  }, [repo, image, tag]);

  const onDelete = async () => {
    setError("");
    try {
      await api.deleteDockerTag(repo, image, tag);
      navigate(imageHref(repo, image));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete tag");
    }
  };

  if (error) return <Card className="p-4 text-sm text-rose-400">{error}</Card>;
  if (!manifest) return null;

  const labels = Object.entries(manifest.labels);

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-neutral-100">
              {image}:{tag}
            </h2>
            <code className="mt-1 block truncate text-xs text-neutral-500">
              {manifest.digest}
            </code>
          </div>
          {canWrite && (
            <Button variant="danger" onClick={onDelete}>
              <Trash2 size={14} className="mr-1.5" />
              Delete tag
            </Button>
          )}
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-3">
          <Field label="Size">{formatSize(manifest.totalSize)}</Field>
          {manifest.created && (
            <Field label="Created">{formatDate(manifest.created)}</Field>
          )}
          {(manifest.os || manifest.architecture) && (
            <Field label="Platform">
              {[manifest.os, manifest.architecture].filter(Boolean).join("/")}
            </Field>
          )}
          <Field label="Layers">
            {manifest.platforms.length > 0
              ? `${manifest.platforms.length} manifests`
              : manifest.layers.length}
          </Field>
        </dl>
      </Card>

      {manifest.platforms.length > 0 && (
        <Card className="p-4">
          <PanelTitle>Platforms</PanelTitle>
          <div className="mt-2 space-y-1">
            {manifest.platforms.map((platform) => (
              <div
                key={platform.digest}
                className="flex items-center gap-2 text-xs text-neutral-400"
              >
                <Badge tone="violet">
                  {[platform.os, platform.architecture, platform.variant]
                    .filter(Boolean)
                    .join("/") || "unknown"}
                </Badge>
                <code className="truncate text-neutral-600">
                  {shortDigest(platform.digest)}
                </code>
              </div>
            ))}
          </div>
        </Card>
      )}

      {manifest.layers.length > 0 && (
        <Card className="p-4">
          <PanelTitle>Layers</PanelTitle>
          <div className="mt-2 space-y-1">
            {manifest.layers.map((layer, index) => (
              <div
                key={`${layer.digest}-${index}`}
                className="flex items-center gap-2 text-xs"
              >
                <Layers size={14} className="shrink-0 text-neutral-600" />
                <code className="truncate text-neutral-500">
                  {shortDigest(layer.digest)}
                </code>
                <span className="ml-auto shrink-0 text-neutral-600">
                  {formatSize(layer.size)}
                </span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {labels.length > 0 && (
        <Card className="p-4">
          <PanelTitle>Labels</PanelTitle>
          <dl className="mt-2 space-y-1 text-xs">
            {labels.map(([key, value]) => (
              <div key={key} className="flex gap-2">
                <dt className="shrink-0 text-neutral-500">{key}</dt>
                <dd className="truncate text-neutral-300">{value}</dd>
              </div>
            ))}
          </dl>
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
