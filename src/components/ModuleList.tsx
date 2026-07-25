import React from "react";

interface ModuleInfo {
  name: string;
  symbolCount: string;
}

const MODULES: ModuleInfo[] = [
  { name: "codewiki-core", symbolCount: "45 symbols" },
  { name: "codewiki-parser", symbolCount: "128 symbols" },
  { name: "codewiki-agent", symbolCount: "67 symbols" },
  { name: "codewiki-web", symbolCount: "23 symbols" },
];

/**
 * Module List — project module breakdown.
 * Matches design: each row 40px height · 20px teal icon · JetBrains Mono name · symbol count
 */
export const ModuleList: React.FC = () => {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-semibold text-[var(--color-text-primary)] tracking-[0.5px] font-primary">
        Modules
      </span>

      <div className="flex flex-col gap-[1px]">
        {MODULES.map((mod) => (
          <div
            key={mod.name}
            className="flex items-center h-[40px] px-3 gap-3 rounded-md hover:bg-[var(--color-border-subtle)] transition-colors"
          >
            {/* Module Icon — teal square */}
            <span className="w-[20px] h-[20px] rounded flex-shrink-0 bg-[var(--color-accent-secondary)]" />

            {/* Module Name — JetBrains Mono */}
            <span className="flex-1 text-[13px] font-normal text-[var(--color-text-primary)] font-mono">
              {mod.name}
            </span>

            {/* Symbol Count */}
            <span className="text-[11px] font-normal text-[var(--color-text-secondary)] font-primary whitespace-nowrap">
              {mod.symbolCount}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};
