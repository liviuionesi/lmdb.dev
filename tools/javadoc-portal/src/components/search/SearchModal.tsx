'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Search, X, Code2, Radio, Zap, ArrowRight, CornerDownLeft } from 'lucide-react';
import { ModuleDoc } from '@/types/schema';

interface SearchResultItem {
  moduleId: string;
  moduleName: string;
  className: string;
  packageName: string;
  kind: string;
  summary: string;
  category: string;
  methodName?: string;
  httpMethod?: string | null;
  httpPath?: string | null;
}

interface SearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  modules: ModuleDoc[];
}

export const SearchModal: React.FC<SearchModalProps> = ({
  isOpen,
  onClose,
  modules,
}) => {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // Flatten all searchable items
  const allItems: SearchResultItem[] = [];
  modules.forEach((mod) => {
    mod.classes.forEach((cls) => {
      // Add class itself
      allItems.push({
        moduleId: mod.id,
        moduleName: mod.name,
        className: cls.name,
        packageName: cls.package,
        kind: cls.kind,
        summary: cls.summary,
        category: cls.category,
      });

      // Add significant methods/endpoints
      cls.methods.forEach((m) => {
        if (m.isEndpoint || m.name !== 'main') {
          allItems.push({
            moduleId: mod.id,
            moduleName: mod.name,
            className: cls.name,
            packageName: cls.package,
            kind: 'method',
            summary: m.summary || `${m.name} method in ${cls.name}`,
            category: cls.category,
            methodName: m.name,
            httpMethod: m.httpMethod,
            httpPath: m.httpPath,
          });
        }
      });
    });
  });

  // Filter items
  const filtered = query.trim() === ''
    ? allItems.slice(0, 8)
    : allItems
        .filter((item) => {
          const q = query.toLowerCase();
          return (
            item.className.toLowerCase().includes(q) ||
            item.packageName.toLowerCase().includes(q) ||
            item.moduleName.toLowerCase().includes(q) ||
            (item.methodName && item.methodName.toLowerCase().includes(q)) ||
            (item.httpPath && item.httpPath.toLowerCase().includes(q)) ||
            item.summary.toLowerCase().includes(q)
          );
        })
        .slice(0, 15);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setSelectedIndex(0);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        if (isOpen) onClose();
        else onClose(); // parent handles toggle
      }
      if (!isOpen) return;

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % (filtered.length || 1));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + filtered.length) % (filtered.length || 1));
      } else if (e.key === 'Enter' && filtered[selectedIndex]) {
        e.preventDefault();
        handleSelect(filtered[selectedIndex]);
      } else if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, filtered, selectedIndex]);

  const handleSelect = (item: SearchResultItem) => {
    const targetAnchor = item.methodName ? `#method-${item.methodName}` : '';
    router.push(`/reference/${item.moduleId}/${item.className}${targetAnchor}`);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150">
      <div
        className="w-full max-w-2xl bg-react-card border border-react-border rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[80vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Input Bar */}
        <div className="flex items-center gap-3 px-4 py-3.5 border-b border-react-border bg-react-bg/80">
          <Search className="w-5 h-5 text-react-cyan shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            placeholder="Search classes, methods, endpoints (e.g. ActorController, getActor, @GetMapping)..."
            className="w-full bg-transparent border-none outline-none text-react-text placeholder:text-react-textSubtle text-base font-sans"
          />
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-react-card text-react-textSubtle hover:text-react-text"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Results List */}
        <div className="overflow-y-auto p-2 space-y-1 flex-1">
          {filtered.length === 0 ? (
            <div className="py-12 text-center text-sm text-react-textSubtle">
              No results found for &ldquo;<span className="text-react-text font-semibold">{query}</span>&rdquo;
            </div>
          ) : (
            filtered.map((item, idx) => {
              const isSelected = idx === selectedIndex;
              return (
                <button
                  key={`${item.moduleId}-${item.className}-${item.methodName || 'cls'}-${idx}`}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  className={`w-full text-left px-3.5 py-2.5 rounded-xl flex items-center justify-between transition-colors ${
                    isSelected
                      ? 'bg-react-cyan/10 border border-react-cyan/40 text-react-text'
                      : 'hover:bg-react-bg/60 text-react-textMuted border border-transparent'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                        item.httpMethod
                          ? 'bg-react-cyan/20 text-react-cyan'
                          : item.kind === 'method'
                          ? 'bg-react-accentYellow/20 text-react-accentYellow'
                          : 'bg-react-card border border-react-border text-react-cyan'
                      }`}
                    >
                      {item.httpMethod ? (
                        <Radio className="w-4 h-4" />
                      ) : item.kind === 'method' ? (
                        <Zap className="w-4 h-4" />
                      ) : (
                        <Code2 className="w-4 h-4" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-semibold text-react-text truncate">
                          {item.className}
                          {item.methodName && (
                            <span className="text-react-cyan">.{item.methodName}()</span>
                          )}
                        </span>
                        {item.httpMethod && (
                          <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-react-cyan/20 text-react-cyan uppercase">
                            {item.httpMethod} {item.httpPath}
                          </span>
                        )}
                        <span className="text-[11px] px-1.5 py-0.5 rounded bg-react-card border border-react-border text-react-textSubtle hidden sm:inline-block">
                          {item.moduleName}
                        </span>
                      </div>
                      <p className="text-xs text-react-textSubtle truncate mt-0.5 font-sans">
                        {item.summary || item.packageName}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0 pl-2">
                    {isSelected && (
                      <span className="flex items-center gap-1 text-[11px] font-mono text-react-cyan">
                        <span>Jump</span>
                        <CornerDownLeft className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </div>
                </button>
              );
            })
          )}
        </div>

        {/* Footer info */}
        <div className="px-4 py-2 border-t border-react-border bg-react-bg/60 flex items-center justify-between text-xs text-react-textSubtle">
          <div className="flex items-center gap-3">
            <span><kbd className="px-1 py-0.5 rounded bg-react-card border border-react-border">↑</kbd> <kbd className="px-1 py-0.5 rounded bg-react-card border border-react-border">↓</kbd> to navigate</span>
            <span><kbd className="px-1.5 py-0.5 rounded bg-react-card border border-react-border">Enter</kbd> to select</span>
            <span><kbd className="px-1.5 py-0.5 rounded bg-react-card border border-react-border">Esc</kbd> to close</span>
          </div>
          <span>Instant Codebase Index</span>
        </div>
      </div>
    </div>
  );
};
