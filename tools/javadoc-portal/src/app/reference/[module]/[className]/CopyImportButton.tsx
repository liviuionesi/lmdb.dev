'use client';

import React, { useState } from 'react';
import { Copy, Check } from 'lucide-react';

interface CopyImportButtonProps {
  importStatement: string;
}

export const CopyImportButton: React.FC<CopyImportButtonProps> = ({
  importStatement,
}) => {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard.writeText(importStatement);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button
      onClick={copy}
      className="inline-flex items-center gap-1.5 text-xs font-mono px-2.5 py-1 rounded-md bg-react-card hover:bg-react-cardHover border border-react-border text-react-textMuted hover:text-react-cyan transition-colors"
      title={importStatement}
    >
      {copied ? (
        <Check className="w-3.5 h-3.5 text-react-accentGreen" />
      ) : (
        <Copy className="w-3.5 h-3.5" />
      )}
      <span>{copied ? 'Copied Import!' : 'Copy Import'}</span>
    </button>
  );
};
