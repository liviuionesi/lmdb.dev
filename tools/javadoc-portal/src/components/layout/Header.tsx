'use client';

import React from 'react';
import Link from 'next/link';
import { Search, Code2, Layers, Github, ExternalLink } from 'lucide-react';

interface HeaderProps {
  projectName: string;
  version: string;
  onOpenSearch: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  projectName,
  version,
  onOpenSearch,
}) => {
  return (
    <header className="sticky top-0 z-40 w-full backdrop-blur border-b border-react-border bg-react-bg/90">
      <div className="max-w-[1600px] mx-auto flex items-center justify-between h-16 px-4 md:px-8">
        {/* Left: Brand / Logo */}
        <div className="flex items-center gap-3">
          <Link href="/" className="flex items-center gap-3 group">
            <div className="w-9 h-9 rounded-xl bg-react-card border border-react-border flex items-center justify-center text-react-cyan shadow-cyan group-hover:scale-105 transition-transform">
              <Code2 className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg text-react-text tracking-tight group-hover:text-react-cyan transition-colors">
                  {projectName}
                </span>
                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-react-card border border-react-border text-react-cyan">
                  {version}
                </span>
              </div>
              <span className="text-xs text-react-textMuted hidden sm:inline-block">
                Reference &amp; Architecture Portal
              </span>
            </div>
          </Link>
        </div>

        {/* Center: Search Trigger (Cmd+K) */}
        <div className="flex-1 max-w-md mx-4 hidden md:block">
          <button
            onClick={onOpenSearch}
            className="w-full flex items-center justify-between px-3.5 py-2 rounded-xl bg-react-card hover:bg-react-cardHover border border-react-border text-sm text-react-textMuted transition-all shadow-sm hover:border-react-cyan/50"
          >
            <div className="flex items-center gap-2.5">
              <Search className="w-4 h-4 text-react-cyan" />
              <span>Search classes, methods, endpoints...</span>
            </div>
            <kbd className="hidden lg:inline-flex items-center gap-1 text-[11px] font-mono px-2 py-0.5 rounded bg-react-bg border border-react-border text-react-textSubtle">
              ⌘K
            </kbd>
          </button>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2 sm:gap-4">
          <button
            onClick={onOpenSearch}
            className="p-2 rounded-lg bg-react-card border border-react-border text-react-textMuted hover:text-react-text md:hidden"
            aria-label="Search"
          >
            <Search className="w-5 h-5" />
          </button>

          <Link
            href="/"
            className="hidden sm:flex items-center gap-1.5 text-sm font-medium text-react-textMuted hover:text-react-text transition-colors px-3 py-1.5 rounded-lg hover:bg-react-card"
          >
            <Layers className="w-4 h-4 text-react-cyan" />
            <span>Modules</span>
          </Link>

          <a
            href="https://github.com/liviuionesi/lmdb.dev"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1.5 text-sm font-medium text-react-textMuted hover:text-react-text transition-colors px-3 py-1.5 rounded-lg hover:bg-react-card border border-transparent hover:border-react-border"
          >
            <Github className="w-4 h-4" />
            <span className="hidden sm:inline">GitHub</span>
          </a>
        </div>
      </div>
    </header>
  );
};
