'use client';

import React, { useState } from 'react';
import { MethodDoc } from '@/types/schema';
import { ParameterTable } from './ParameterTable';
import { Copy, Check, Radio, ArrowRight, CornerDownRight } from 'lucide-react';

interface MethodSignatureProps {
  method: MethodDoc;
}

export const MethodSignature: React.FC<MethodSignatureProps> = ({ method }) => {
  const [copied, setCopied] = useState(false);

  const copySignature = () => {
    navigator.clipboard.writeText(method.signature);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div id={`method-${method.name}`} className="my-8 scroll-mt-24 border border-react-border rounded-2xl bg-react-card/50 overflow-hidden shadow-card">
      {/* Method Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 bg-react-card border-b border-react-border">
        <div className="flex items-center gap-2.5">
          {method.httpMethod && (
            <span
              className={`text-xs font-bold px-2 py-0.5 rounded-md uppercase tracking-wide flex items-center gap-1.5 ${
                method.httpMethod === 'GET'
                  ? 'bg-react-cyan/20 text-react-cyan border border-react-cyan/30'
                  : method.httpMethod === 'POST'
                  ? 'bg-react-accentGreen/20 text-react-accentGreen border border-react-accentGreen/30'
                  : method.httpMethod === 'DELETE'
                  ? 'bg-react-accentRed/20 text-react-accentRed border border-react-accentRed/30'
                  : 'bg-react-accentYellow/20 text-react-accentYellow border border-react-accentYellow/30'
              }`}
            >
              <Radio className="w-3.5 h-3.5" />
              <span>{method.httpMethod}</span>
              {method.httpPath && <span className="font-mono text-[11px] lowercase">{method.httpPath}</span>}
            </span>
          )}
          <h3 className="font-mono text-base font-bold text-react-text">
            {method.name}()
          </h3>
        </div>

        <button
          onClick={copySignature}
          className="flex items-center gap-1.5 text-xs text-react-textSubtle hover:text-react-text px-2.5 py-1 rounded-lg bg-react-bg/60 border border-react-borderSubtle transition-colors"
          title="Copy method signature"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-react-accentGreen" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied!' : 'Copy'}</span>
        </button>
      </div>

      <div className="p-5 space-y-4">
        {/* Method Javadoc Summary */}
        <p className="text-sm text-react-text leading-relaxed">
          {method.summary || method.description}
        </p>

        {/* Signature Code Block */}
        <div className="p-4 rounded-xl bg-react-code border border-react-borderSubtle font-mono text-xs overflow-x-auto text-react-text">
          <div className="text-react-textSubtle mb-1 text-[11px]">// Java Signature</div>
          <span className="text-react-cyan">{method.returnType}</span>{' '}
          <span className="font-bold text-react-text">{method.name}</span>
          <span className="text-react-textSubtle">(</span>
          {method.parameters.map((p, idx) => (
            <span key={p.name}>
              <span className="text-react-accentYellow">{p.type}</span>{' '}
              <span className="text-react-text">{p.name}</span>
              {idx < method.parameters.length - 1 && <span className="text-react-textSubtle">, </span>}
            </span>
          ))}
          <span className="text-react-textSubtle">)</span>
          {method.throws && (
            <span className="text-react-accentRed"> throws {method.throws}</span>
          )}
        </div>

        {/* Parameters Section */}
        {method.parameters.length > 0 && (
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-react-textSubtle mb-2">
              Parameters
            </h4>
            <ParameterTable parameters={method.parameters} />
          </div>
        )}

        {/* Returns Section */}
        <div className="pt-2 border-t border-react-borderSubtle/60 flex items-start gap-2 text-xs">
          <span className="font-bold text-react-textSubtle shrink-0">Returns:</span>
          <span className="font-mono text-react-cyan font-semibold">{method.returnType}</span>
          {method.returnDescription && (
            <span className="text-react-textMuted">&mdash; {method.returnDescription}</span>
          )}
        </div>

        {/* Throws Section */}
        {method.throws && (
          <div className="flex items-start gap-2 text-xs text-react-accentRed">
            <span className="font-bold shrink-0">Throws:</span>
            <span className="font-mono font-semibold">{method.throws}</span>
          </div>
        )}
      </div>
    </div>
  );
};
