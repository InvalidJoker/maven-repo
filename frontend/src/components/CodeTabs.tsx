import { useState } from "react";
import { CopyButton } from "./CopyButton";

export interface CodeTab {
  id: string;
  label: string;
  code: string;
}

export function CodeTabs({ tabs }: { tabs: CodeTab[] }) {
  const [active, setActive] = useState(tabs[0]?.id);
  const code = (tabs.find((tab) => tab.id === active) ?? tabs[0])?.code ?? "";

  return (
    <div>
      {tabs.length > 1 && (
        <div className="mb-2 flex flex-wrap gap-1">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActive(tab.id)}
              className={`rounded px-2 py-1 text-xs transition-colors ${
                tab.id === active
                  ? "bg-brand-500 text-white"
                  : "text-neutral-400 hover:bg-neutral-800"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}
      <div className="relative">
        <CopyButton text={code} />
        <pre className="overflow-x-auto rounded-md border border-neutral-800 bg-neutral-950 p-4 pr-12 text-xs leading-relaxed text-neutral-300">
          {code}
        </pre>
      </div>
    </div>
  );
}
