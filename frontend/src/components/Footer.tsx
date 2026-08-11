import { Github, type LucideIcon } from "lucide-react";
import { useInstance } from "../instance";
import { icon } from "../icons";

export const SOURCE_URL = "https://github.com/InvalidJoker/artifact-forge";

export function Footer() {
  const { footer } = useInstance();

  if (!footer.showSource && footer.links.length === 0) return null;

  return (
    <footer className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-center gap-x-6 gap-y-2 border-t border-neutral-800 px-4 py-4">
      {footer.showSource && (
        <FooterLink
          href={SOURCE_URL}
          label="Source"
          Icon={Github}
        />
      )}
      {footer.links.map((link) => (
        <FooterLink
          key={`${link.label}:${link.url}`}
          href={link.url}
          label={link.label}
          Icon={icon(link.icon)}
        />
      ))}
    </footer>
  );
}

function FooterLink({
  href,
  label,
  Icon,
}: {
  href: string;
  label: string;
  Icon: LucideIcon;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="flex items-center gap-2 text-sm text-neutral-500 transition-colors hover:text-neutral-300"
    >
      <Icon size={16} />
      {label}
    </a>
  );
}
