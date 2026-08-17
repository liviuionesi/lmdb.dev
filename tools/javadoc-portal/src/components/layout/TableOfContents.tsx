'use client';

import React, { useEffect, useState } from 'react';
import { AlignLeft, Hash } from 'lucide-react';

interface TocItem {
  id: string;
  title: string;
  level: number;
}

interface TableOfContentsProps {
  items: TocItem[];
}

export const TableOfContents: React.FC<TableOfContentsProps> = ({ items }) => {
  const [activeId, setActiveId] = useState<string>('');

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id);
          }
        });
      },
      { rootMargin: '-80px 0% -60% 0%' }
    );

    items.forEach((item) => {
      const el = document.getElementById(item.id);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, [items]);

  if (items.length === 0) return null;

  return (
    <div className="hidden xl:block w-64 shrink-0 sticky top-20 h-[calc(100vh-5rem)] overflow-y-auto px-4 py-2">
      <div className="flex items-center gap-2 mb-3 text-xs font-bold uppercase tracking-wider text-react-textSubtle">
        <AlignLeft className="w-4 h-4 text-react-cyan" />
        <span>On this page</span>
      </div>
      <nav className="space-y-1 text-sm border-l border-react-border pl-3">
        {items.map((item) => {
          const isActive = activeId === item.id;
          return (
            <a
              key={item.id}
              href={`#${item.id}`}
              className={`block py-1 transition-colors text-xs truncate ${
                item.level === 2 ? 'font-medium' : 'pl-3 text-[11px]'
              } ${
                isActive
                  ? 'text-react-cyan font-semibold translate-x-1'
                  : 'text-react-textMuted hover:text-react-text'
              }`}
            >
              {item.title}
            </a>
          );
        })}
      </nav>
    </div>
  );
};
