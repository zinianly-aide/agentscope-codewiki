import React from "react";
import { RiskBadge } from "./RiskBadge";
import type { RiskLevel } from "./RiskBadge";

interface AnalysisEntry {
  name: string;
  time: string;
  risk: RiskLevel;
  completed: boolean;
}

const ANALYSES: AnalysisEntry[] = [
  { name: "agentscope-codewiki v2.1.0", time: "2h ago", risk: "LOW", completed: true },
  { name: "spring-boot-demo main", time: "5h ago", risk: "MEDIUM", completed: true },
  { name: "react-admin feat/auth", time: "1d ago", risk: "HIGH", completed: false },
];

/**
 * Recent Analyses — analysis history list.
 * Matches design: 48px rows · 8px status dot · rounded-md · risk badges with semitransparent backgrounds
 */
export const RecentAnalyses: React.FC = () => {
  return (
    <div className="flex flex-col gap-3 p-5 rounded-lg bg-[var(--color-bg-card)] border border-[var(--color-border-subtle)]">
      <span className="text-sm font-semibold text-[var(--color-text-primary)] font-primary">
        Recent Analyses
      </span>

      <div className="flex flex-col gap-0">
        {ANALYSES.map((entry, i) => (
          <div
            key={entry.name}
            className={`flex items-center h-[48px] px-3 gap-3 rounded-md ${
              i > 0 ? "border-t border-[var(--color-border-subtle)]" : "border border-[var(--color-border-subtle)]"
            }`}
          >
            {/* Status Dot */}
            <span
              className={`w-2 h-2 rounded-full shrink-0 ${
                entry.completed
                  ? "bg-[var(--color-accent-secondary)]"
                  : "bg-[var(--color-text-secondary)]"
              }`}
            />

            {/* Analysis Name */}
            <span className="flex-1 text-[13px] font-medium text-[var(--color-text-primary)] font-primary truncate">
              {entry.name}
            </span>

            {/* Time */}
            <span className="text-[11px] font-normal text-[var(--color-text-secondary)] font-primary whitespace-nowrap">
              {entry.time}
            </span>

            {/* Risk Badge */}
            <RiskBadge level={entry.risk} />
          </div>
        ))}
      </div>
    </div>
  );
};
