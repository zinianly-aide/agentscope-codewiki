import React from "react";
import { cn } from "../utils";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

const RISK_STYLES: Record<RiskLevel, { bg: string; text: string }> = {
  LOW: { bg: "bg-[var(--color-success)]/15", text: "text-[var(--color-success)]" },
  MEDIUM: { bg: "bg-[var(--color-warning)]/15", text: "text-[var(--color-warning)]" },
  HIGH: { bg: "bg-[var(--color-error)]/15", text: "text-[var(--color-error)]" },
  CRITICAL: { bg: "bg-[var(--color-error)]/15", text: "text-[var(--color-error)]" },
};

interface RiskBadgeProps {
  level: RiskLevel;
}

/**
 * Risk Badge — rounded pill with semi-transparent bg.
 * Matches design: radius 4px · padding 6px · Inter SemiBold 10px
 */
export const RiskBadge: React.FC<RiskBadgeProps> = ({ level }) => {
  const style = RISK_STYLES[level];
  return (
    <span
      className={cn(
        "inline-flex items-center justify-center h-[22px] px-[6px] rounded text-[10px] font-semibold font-primary",
        style.bg,
        style.text
      )}
    >
      {level}
    </span>
  );
};
