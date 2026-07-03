import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { api, ApiError, type AccentColor } from "../api";
import { useInstance } from "../instance";
import { ACCENT_OPTIONS, ACCENT_PALETTE } from "../accents";
import { AdminNav } from "../components/AdminNav";
import { Button, Card, ErrorText, Input } from "../ui";

type LogoAction =
  | { type: "keep" }
  | { type: "url"; url: string }
  | { type: "upload"; file: File; preview: string }
  | { type: "reset" };

export function AdminConfig() {
  const {
    name: savedName,
    iconUrl: savedIcon,
    accent: savedAccent,
    refresh,
  } = useInstance();

  const [name, setName] = useState(savedName);
  const [accent, setAccent] = useState<AccentColor>(savedAccent);
  const [logo, setLogo] = useState<LogoAction>({ type: "keep" });
  const [urlDraft, setUrlDraft] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setName(savedName);
    setAccent(savedAccent);
    setLogo({ type: "keep" });
    setUrlDraft("");
    if (fileRef.current) fileRef.current.value = "";
  }, [savedName, savedAccent, savedIcon]);

  const dirty =
    name.trim() !== savedName || accent !== savedAccent || logo.type !== "keep";

  const previewIcon =
    logo.type === "upload"
      ? logo.preview
      : logo.type === "url"
        ? logo.url || null
        : logo.type === "reset"
          ? null
          : savedIcon;

  const onPickFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setLogo({ type: "upload", file, preview: URL.createObjectURL(file) });
    setUrlDraft("");
  };

  const onUrlChange = (value: string) => {
    setUrlDraft(value);
    setLogo(
      value.trim() ? { type: "url", url: value.trim() } : { type: "keep" },
    );
    if (fileRef.current) fileRef.current.value = "";
  };

  const onRemoveLogo = () => {
    setLogo({ type: "reset" });
    setUrlDraft("");
    if (fileRef.current) fileRef.current.value = "";
  };

  const discard = () => {
    setName(savedName);
    setAccent(savedAccent);
    setLogo({ type: "keep" });
    setUrlDraft("");
    if (fileRef.current) fileRef.current.value = "";
  };

  const save = async () => {
    setBusy(true);
    setError("");
    try {
      if (name.trim() && name.trim() !== savedName)
        await api.updateInstanceName(name.trim());
      if (accent !== savedAccent) await api.setInstanceAccent(accent);
      if (logo.type === "url") await api.setInstanceIconUrl(logo.url);
      else if (logo.type === "upload") await api.uploadInstanceIcon(logo.file);
      else if (logo.type === "reset") await api.resetInstanceIcon();
      await refresh();
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Failed to save settings",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <AdminNav active="config" />

      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-neutral-100">
            Configuration
          </h1>
          <p className="mt-1 text-sm text-neutral-500">
            Branding for this repository instance.
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {dirty && (
            <span className="inline-flex items-center gap-1.5 text-xs text-amber-400">
              <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
              Unsaved changes
            </span>
          )}
          <Button variant="ghost" onClick={discard} disabled={!dirty || busy}>
            Discard
          </Button>
          <Button onClick={save} disabled={!dirty || busy}>
            {busy ? "Saving…" : "Save changes"}
          </Button>
        </div>
      </div>

      <Card className="mb-6 p-4">
        <h2 className="mb-3 text-sm font-semibold text-neutral-200">Name</h2>
        <Input
          placeholder="Repository name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="max-w-sm"
        />
        <p className="mt-2 text-xs text-neutral-500">
          Shown in the header and the browser tab title.
        </p>
      </Card>

      <Card className="mb-6 p-4">
        <h2 className="mb-3 text-sm font-semibold text-neutral-200">Logo</h2>
        <div className="flex items-start gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-md border border-neutral-700 bg-neutral-900">
            {previewIcon ? (
              <img
                src={previewIcon}
                alt="logo"
                className="h-full w-full object-contain"
              />
            ) : (
              <span className="text-xs text-neutral-600">none</span>
            )}
          </div>

          <div className="min-w-0 flex-1 space-y-4">
            <div>
              <label className="mb-1 block text-xs text-neutral-500">
                Image URL
              </label>
              <Input
                type="url"
                placeholder="https://avatars.githubusercontent.com/u/83445245"
                value={urlDraft}
                onChange={(e) => onUrlChange(e.target.value)}
                className="max-w-md"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs text-neutral-500">
                …or upload an image
              </label>
              <div className="flex flex-wrap items-center gap-2">
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/*"
                  onChange={onPickFile}
                  className="text-sm text-neutral-400 file:mr-3 file:rounded-md file:border-0 file:bg-brand-500 file:px-3 file:py-1.5 file:text-sm file:text-white hover:file:bg-brand-400"
                />
                {previewIcon && (
                  <Button variant="ghost" onClick={onRemoveLogo}>
                    Remove logo
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>
        <p className="mt-3 text-xs text-neutral-500">
          Used as the app logo and browser favicon. Uploads are limited to
          1&nbsp;MB.
        </p>
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 text-sm font-semibold text-neutral-200">
          Accent color
        </h2>
        <div className="flex flex-wrap gap-3">
          {ACCENT_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setAccent(option)}
              title={option.toLowerCase()}
              aria-label={option.toLowerCase()}
              className={`h-9 w-9 rounded-full ring-2 ring-offset-2 ring-offset-neutral-900 transition ${
                accent === option
                  ? "ring-white"
                  : "ring-transparent hover:ring-neutral-600"
              }`}
              style={{ backgroundColor: ACCENT_PALETTE[option][2] }}
            />
          ))}
        </div>
        <p className="mt-3 text-xs text-neutral-500">
          Used for buttons, links and highlights across the app.
        </p>
      </Card>

      <div className="mt-4">
        <ErrorText>{error}</ErrorText>
      </div>
    </div>
  );
}
