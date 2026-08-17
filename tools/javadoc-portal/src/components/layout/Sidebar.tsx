'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ModuleDoc, ClassDoc } from '@/types/schema';
import {
  ChevronDown,
  ChevronRight,
  Server,
  FolderTree,
  FileCode2,
  Boxes,
  Zap,
  Sliders,
  Radio,
} from 'lucide-react';

interface SidebarProps {
  modules: ModuleDoc[];
}

export const Sidebar: React.FC<SidebarProps> = ({ modules }) => {
  const pathname = usePathname();
  // Open active module or default first
  const [expandedModules, setExpandedModules] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    modules.forEach((mod, idx) => {
      // expand if path matches, or first 2 by default
      if (pathname.includes(`/reference/${mod.id}`) || idx < 3) {
        initial[mod.id] = true;
      }
    });
    return initial;
  });

  const toggleModule = (moduleId: string) => {
    setExpandedModules((prev) => ({
      ...prev,
      [moduleId]: !prev[moduleId],
    }));
  };

  const getCategoryIcon = (cat: string) => {
    if (cat.includes('Controller')) return <Radio className="w-3.5 h-3.5 text-react-cyan" />;
    if (cat.includes('Service')) return <Zap className="w-3.5 h-3.5 text-react-accentYellow" />;
    if (cat.includes('Model') || cat.includes('Entity')) return <Boxes className="w-3.5 h-3.5 text-react-accentGreen" />;
    if (cat.includes('DTO')) return <FileCode2 className="w-3.5 h-3.5 text-react-textMuted" />;
    if (cat.includes('Config')) return <Sliders className="w-3.5 h-3.5 text-react-accentPurple" />;
    return <FolderTree className="w-3.5 h-3.5 text-react-textSubtle" />;
  };

  return (
    <aside className="w-72 lg:w-80 shrink-0 border-r border-react-border bg-react-bg/60 overflow-y-auto h-[calc(100vh-4rem)] sticky top-16 p-4">
      <div className="mb-4 px-2">
        <span className="text-xs font-bold uppercase tracking-wider text-react-textSubtle">
          Microservices &amp; Modules
        </span>
      </div>

      <nav className="space-y-3">
        {modules.map((mod) => {
          const isExpanded = !!expandedModules[mod.id];
          
          // Group classes by category
          const categories: Record<string, ClassDoc[]> = {};
          mod.classes.forEach((c) => {
            if (!categories[c.category]) categories[c.category] = [];
            categories[c.category].push(c);
          });

          return (
            <div key={mod.id} className="rounded-xl overflow-hidden bg-react-card/40 border border-react-borderSubtle">
              <button
                onClick={() => toggleModule(mod.id)}
                className="w-full flex items-center justify-between px-3 py-2.5 hover:bg-react-card/80 transition-colors text-left group"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <Server className="w-4 h-4 text-react-cyan shrink-0" />
                  <span className="font-semibold text-sm text-react-text group-hover:text-react-cyan transition-colors truncate">
                    {mod.name}
                  </span>
                  {mod.port !== 'N/A' && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-react-card border border-react-border text-react-textSubtle shrink-0">
                      :{mod.port}
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <span className="text-[11px] text-react-textSubtle">
                    {mod.classes.length}
                  </span>
                  {isExpanded ? (
                    <ChevronDown className="w-4 h-4 text-react-textSubtle" />
                  ) : (
                    <ChevronRight className="w-4 h-4 text-react-textSubtle" />
                  )}
                </div>
              </button>

              {isExpanded && (
                <div className="px-2 pb-2.5 pt-1 space-y-3 border-t border-react-borderSubtle/60">
                  {Object.entries(categories).map(([categoryName, classes]) => (
                    <div key={categoryName} className="space-y-1">
                      <div className="flex items-center gap-1.5 px-2 py-1 text-[11px] font-medium text-react-textSubtle uppercase tracking-wider">
                        {getCategoryIcon(categoryName)}
                        <span>{categoryName}</span>
                      </div>
                      <div className="space-y-0.5 pl-2">
                        {classes.map((cls) => {
                          const href = `/reference/${mod.id}/${cls.name}`;
                          const isActive = pathname === href;

                          return (
                            <Link
                              key={cls.id}
                              href={href}
                              className={`block px-2.5 py-1.5 rounded-lg text-xs font-mono transition-all truncate ${
                                isActive
                                  ? 'bg-react-cyan/10 text-react-cyan font-semibold border-l-2 border-react-cyan shadow-cyan'
                                  : 'text-react-textMuted hover:text-react-text hover:bg-react-card'
                              }`}
                              title={`${cls.package}.${cls.name}`}
                            >
                              <span className="opacity-70 text-[10px] mr-1">
                                {cls.kind === 'interface' ? 'I' : cls.kind === 'record' ? 'R' : 'C'}
                              </span>
                              {cls.name}
                            </Link>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>
    </aside>
  );
};
