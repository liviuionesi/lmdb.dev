'use client';

import React, { useState } from 'react';
import { Code, ChevronDown, ChevronUp, Copy, Check } from 'lucide-react';

interface SourceViewerProps {
  sourceCode: string;
  filePath: string;
}

export const SourceViewer: React.FC<SourceViewerProps> = ({
  sourceCode,
  filePath,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  const copySource = () => {
    navigator.clipboard.writeText(sourceCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const lines = sourceCode.split('\n');

  return (
    <div id="source-code" className="my-8 scroll-mt-24 rounded-2xl border border-react-border bg-react-card overflow-hidden shadow-card">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between px-5 py-3.5 bg-react-card hover:bg-react-cardHover transition-colors text-left"
      >
        <div className="flex items-center gap-2.5">
          <Code className="w-4 h-4 text-react-cyan" />
          <span className="font-semibold text-sm text-react-text">
            Complete Java Source Code
          </span>
          <span className="text-xs text-react-textSubtle font-mono">({filePath})</span>
        </div>
        <div className="flex items-center gap-2 text-xs text-react-cyan font-medium">
          <span>{isOpen ? 'Collapse Source' : 'Expand Source'}</span>
          {isOpen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </div>
      </button>

      {isOpen && (
        <div className="border-t border-react-border bg-react-code">
          <div className="flex justify-end p-2 border-b border-react-borderSubtle bg-react-bg/60">
            <button
              onClick={copySource}
              className="flex items-center gap-1.5 text-xs text-react-textSubtle hover:text-react-text px-2.5 py-1 rounded-lg hover:bg-react-card transition-colors"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-react-accentGreen" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied Full Source' : 'Copy File'}</span>
            </button>
          </div>

          <div className="p-4 overflow-x-auto font-mono text-xs max-h-[600px] overflow-y-auto">
            <table className="w-full border-collapse">
              <tbody>
                {lines.map((line, idx) => (
                  <tr key={idx} className="hover:bg-react-card/40">
                    <td className="w-12 select-none text-right pr-4 text-react-textSubtle opacity-50 text-[11px]">
                      {idx + 1}
                    </td>
                    <td className="text-react-text whitespace-pre font-mono">
                      {line || ' '}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
