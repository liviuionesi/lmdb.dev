import React from 'react';
import { Parameter } from '@/types/schema';

interface ParameterTableProps {
  parameters: Parameter[];
}

export const ParameterTable: React.FC<ParameterTableProps> = ({ parameters }) => {
  if (parameters.length === 0) {
    return (
      <p className="text-xs text-react-textSubtle italic my-2">
        This method does not take any parameters.
      </p>
    );
  }

  return (
    <div className="my-4 overflow-x-auto rounded-xl border border-react-border bg-react-card/40">
      <table className="w-full text-left text-xs border-collapse font-sans">
        <thead>
          <tr className="border-b border-react-border bg-react-card/80 text-react-textSubtle uppercase tracking-wider font-semibold">
            <th className="py-2.5 px-4 w-1/4">Parameter</th>
            <th className="py-2.5 px-4 w-1/4">Type</th>
            <th className="py-2.5 px-4 w-1/2">Description</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-react-borderSubtle">
          {parameters.map((param) => (
            <tr key={param.name} className="hover:bg-react-card/60 transition-colors">
              <td className="py-3 px-4 font-mono font-semibold text-react-text">
                {param.name}
              </td>
              <td className="py-3 px-4 font-mono text-react-cyan">
                <span className="px-1.5 py-0.5 rounded bg-react-cyan/10 border border-react-cyan/20">
                  {param.type}
                </span>
              </td>
              <td className="py-3 px-4 text-react-textMuted leading-relaxed">
                {param.description || <span className="text-react-textSubtle italic">No description provided</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
