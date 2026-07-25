import React from "react";
import { Input } from "antd";
import { SearchOutlined } from "@ant-design/icons";

interface TopToolbarProps {
  onSearch?: (value: string) => void;
}

/**
 * Top Toolbar — 48px height, logo + search + user area.
 * Matches design: bg #101113 · 1px bottom border #1C1D1F · gap 12px · padding 16px
 */
export const TopToolbar: React.FC<TopToolbarProps> = ({ onSearch }) => {
  return (
    <header className="flex items-center h-[48px] px-4 gap-3 bg-[var(--color-bg-card)] border-b border-[var(--color-border-subtle)] shrink-0 font-primary">
      {/* Logo Area — 160px */}
      <div className="flex items-center gap-2 w-[160px]">
        <div className="w-6 h-6 rounded-[6px] bg-[var(--color-accent-primary)] shrink-0" />
        <span className="text-base font-semibold text-[var(--color-text-primary)] truncate">
          CodeWiki
        </span>
      </div>

      {/* Search Bar — flex-1, max 480px */}
      <div className="flex-1 flex justify-center">
        <Input
          prefix={<SearchOutlined className="text-[var(--color-text-secondary)] text-xs" />}
          placeholder="Ask about your codebase…"
          suffix={<kbd className="text-[11px] text-[var(--color-text-secondary)] font-medium">⌘K</kbd>}
          onPressEnter={(e) => onSearch?.(e.currentTarget.value)}
          className="max-w-[480px] h-[32px] rounded-[6px] font-primary"
          classNames={{
            affixWrapper:
              "!bg-[var(--color-bg-search)] !border-none !text-[13px] !text-[var(--color-text-secondary)]",
          }}
        />
      </div>

      {/* User Area */}
      <div className="flex items-center gap-2">
        <div className="w-[28px] h-[28px] rounded-full bg-[var(--color-accent-secondary)] shrink-0" />
        <span className="text-[13px] font-medium text-[var(--color-text-primary)] hidden sm:inline">
          Dev
        </span>
      </div>
    </header>
  );
};
