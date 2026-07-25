import React from "react";
import { cn } from "../utils";

type NavItem =
  | "overview"
  | "architecture"
  | "modules"
  | "impact"
  | "wiki";

const NAV_ITEMS: { key: NavItem; label: string }[] = [
  { key: "overview", label: "Overview" },
  { key: "architecture", label: "Architecture" },
  { key: "modules", label: "Modules" },
  { key: "impact", label: "Impact Analysis" },
  { key: "wiki", label: "Wiki" },
];

interface SidebarProps {
  activeNav: NavItem;
  onNavChange: (nav: NavItem) => void;
}

/**
 * Sidebar — 256px wide pill-style navigation.
 * Matches design: bg #101113 · border #1C1D1F · padding 16px · gap 4px
 * Active pill: bg #5E6AD2 · text #F7F8F8 · radius 8px
 */
export const Sidebar: React.FC<SidebarProps> = ({ activeNav, onNavChange }) => {
  return (
    <aside className="flex flex-col w-[256px] h-full bg-[var(--color-bg-card)] border-r border-[var(--color-border-subtle)] p-4 gap-1 shrink-0 font-primary">
      {/* Section Label */}
      <span
        className="text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.06em] uppercase px-1 mb-2"
      >
        Repositories
      </span>

      {/* Nav Items */}
      {NAV_ITEMS.map(({ key, label }) => {
        const isActive = activeNav === key;
        return (
          <button
            key={key}
            onClick={() => onNavChange(key)}
            className={cn(
              "flex items-center gap-[10px] w-full h-[36px] px-3 rounded-lg text-left transition-colors",
              isActive
                ? "bg-[var(--color-accent-primary)] text-[var(--color-text-primary)]"
                : "text-[var(--color-text-secondary)] hover:bg-[var(--color-border-subtle)]"
            )}
          >
            {/* Icon placeholder */}
            <span
              className={cn(
                "w-4 h-4 rounded-[3px] shrink-0",
                isActive
                  ? "bg-[var(--color-text-primary)]"
                  : "bg-[var(--color-text-secondary)]"
              )}
            />
            <span className={cn(
              "text-sm",
              isActive ? "font-medium" : "font-normal"
            )}>
              {label}
            </span>
          </button>
        );
      })}
    </aside>
  );
};
