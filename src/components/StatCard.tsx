import React from "react";
import { cn } from "../utils";

interface StatCardProps {
  value: string;
  label: string;
  valueColor?: string;
}

/**
 * Stat Card — metric value + label.
 * Matches design: bg #101113 · border #1C1D1F · radius 8px · padding 16px · gap 4px
 * Value: Inter SemiBold 28px · Label: Inter Regular 12px
 */
export const StatCard: React.FC<StatCardProps> = ({
  value,
  label,
  valueColor,
}) => {
  return (
    <div className="flex flex-col flex-1 p-4 gap-1 rounded-lg bg-[var(--color-bg-card)] border border-[var(--color-border-subtle)] font-primary">
      <span
        className={cn(
          "text-[28px] font-semibold leading-tight",
          valueColor ?? "text-[var(--color-text-primary)]"
        )}
      >
        {value}
      </span>
      <span className="text-xs font-normal text-[var(--color-text-secondary)]">
        {label}
      </span>
    </div>
  );
};
