'use client';

import React, { useState } from 'react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { SearchModal } from '../search/SearchModal';
import { ProjectSchema } from '@/types/schema';

interface AppShellProps {
  schema: ProjectSchema;
  children: React.ReactNode;
}

export const AppShell: React.FC<AppShellProps> = ({ schema, children }) => {
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  return (
    <div className="min-h-screen bg-react-bg text-react-text flex flex-col selection:bg-react-cyan/30 selection:text-react-cyan">
      <Header
        projectName={schema.projectName}
        version={schema.version}
        onOpenSearch={() => setIsSearchOpen(true)}
      />

      <div className="flex-1 max-w-[1600px] w-full mx-auto flex">
        <Sidebar modules={schema.modules} />
        <main className="flex-1 min-w-0 px-6 py-8 md:px-10 lg:px-12">
          {children}
        </main>
      </div>

      <SearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        modules={schema.modules}
      />
    </div>
  );
};
