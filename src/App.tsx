import React, { useState } from "react";
import { ConfigProvider, theme } from "antd";
import { Sidebar } from "./components/Sidebar";
import { TopToolbar } from "./components/TopToolbar";
import { StatCard } from "./components/StatCard";
import { ModuleList } from "./components/ModuleList";
import { RecentAnalyses } from "./components/RecentAnalyses";

type NavItem =
  | "overview"
  | "architecture"
  | "modules"
  | "impact"
  | "wiki";

/**
 * AgentScope CodeWiki — Dashboard
 * Pixel-perfect React implementation from .ardot design.
 *
 * Design system:
 * - Background: #08090A / Cards: #101113 / Workspace: #0B0C0D
 * - Accent: #5E6AD2 (indigo) / Secondary: #26A69A (teal)
 * - Typography: Inter (body) + JetBrains Mono (code)
 * - Sidebar: 256px · Toolbar: 48px · Card radius: 8px
 */
const App: React.FC = () => {
  const [activeNav, setActiveNav] = useState<NavItem>("overview");

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: "#5E6AD2",
          colorBgContainer: "#101113",
          colorBorder: "#1C1D1F",
          colorText: "#F7F8F8",
          colorTextSecondary: "#A1A1AA",
          borderRadius: 6,
        },
      }}
    >
      {/* Root Container — 1440×900 scale with min-height fallback */}
      <div className="flex flex-col w-full h-screen min-h-[900px] bg-[var(--color-bg-primary)] font-primary overflow-hidden">
        {/* Top Toolbar — 48px */}
        <TopToolbar />

        {/* Content Area — flex row */}
        <div className="flex flex-1 min-h-0">
          {/* Sidebar — 256px */}
          <Sidebar activeNav={activeNav} onNavChange={setActiveNav} />

          {/* Main Workspace — bg #0B0C0D · padding 24px · gap 16px */}
          <main className="flex flex-col flex-1 p-6 gap-4 overflow-auto bg-[var(--color-bg-workspace)]">
            {/* Page Header — space-between */}
            <div className="flex items-center justify-between">
              <h1 className="text-[22px] font-semibold text-[var(--color-text-primary)] font-primary leading-tight">
                Repository Overview
              </h1>

              <button className="flex items-center gap-[6px] h-[32px] px-3 rounded-md bg-[var(--color-accent-primary)] hover:opacity-90 transition-opacity font-primary">
                <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                  +
                </span>
                <span className="text-[13px] font-medium text-[var(--color-text-primary)]">
                  New Analysis
                </span>
              </button>
            </div>

            {/* Stats Row — 4 cards · gap 12px */}
            <div className="flex gap-3">
              <StatCard value="247" label="Total Files" />
              <StatCard value="1,832" label="Total Symbols" />
              <StatCard value="12" label="Modules" />
              <StatCard
                value="LOW"
                label="Risk Level"
                valueColor="text-[var(--color-accent-secondary)]"
              />
            </div>

            {/* Main Content Card — Project Summary + Modules */}
            <div className="flex flex-col flex-1 p-6 gap-4 rounded-lg bg-[var(--color-bg-card)] border border-[var(--color-border-subtle)] overflow-auto min-h-0">
              {/* Project Summary */}
              <div className="flex flex-col gap-4">
                <h2 className="text-base font-semibold text-[var(--color-text-primary)] font-primary">
                  Project Summary
                </h2>
                <p className="text-sm font-normal text-[var(--color-text-secondary)] leading-[22px] font-primary">
                  AgentScope CodeWiki transforms Git repositories into
                  interactive, queryable knowledge bases. The system parses
                  Java, Python, and TypeScript code, builds a knowledge graph
                  with hybrid retrieval, and generates comprehensive wiki
                  documentation.
                </p>

                {/* Language Tags */}
                <div className="flex gap-2">
                  {[
                    { lang: "Java 21", accent: true },
                    { lang: "Python 3.12", accent: false },
                    { lang: "TypeScript 5.2", accent: false },
                  ].map(({ lang, accent }) => (
                    <span
                      key={lang}
                      className={`inline-flex items-center h-6 px-2 rounded text-[11px] font-medium font-mono ${accent ? "text-[var(--color-accent-primary)]" : "text-[var(--color-text-secondary)]"} bg-[var(--color-border-subtle)]`}
                    >
                      {lang}
                    </span>
                  ))}
                </div>
              </div>

              {/* Module List */}
              <ModuleList />
            </div>

            {/* Recent Analyses */}
            <RecentAnalyses />
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default App;
