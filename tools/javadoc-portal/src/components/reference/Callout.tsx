import React from 'react';
import { Info, AlertTriangle, Lightbulb, Compass } from 'lucide-react';

export type CalloutType = 'note' | 'pitfall' | 'tip' | 'deep-dive';

interface CalloutProps {
  type?: CalloutType;
  title?: string;
  children: React.ReactNode;
}

export const Callout: React.FC<CalloutProps> = ({
  type = 'note',
  title,
  children,
}) => {
  const getStyles = () => {
    switch (type) {
      case 'pitfall':
        return {
          border: 'border-react-accentRed/40 bg-react-accentRed/5',
          titleColor: 'text-react-accentRed',
          icon: <AlertTriangle className="w-5 h-5 text-react-accentRed shrink-0 mt-0.5" />,
          defaultTitle: 'Pitfall',
        };
      case 'tip':
        return {
          border: 'border-react-accentYellow/40 bg-react-accentYellow/5',
          titleColor: 'text-react-accentYellow',
          icon: <Lightbulb className="w-5 h-5 text-react-accentYellow shrink-0 mt-0.5" />,
          defaultTitle: 'Lead Developer Tip',
        };
      case 'deep-dive':
        return {
          border: 'border-react-accentPurple/40 bg-react-accentPurple/5',
          titleColor: 'text-react-accentPurple',
          icon: <Compass className="w-5 h-5 text-react-accentPurple shrink-0 mt-0.5" />,
          defaultTitle: 'Deep Dive',
        };
      case 'note':
      default:
        return {
          border: 'border-react-cyan/40 bg-react-cyan/5',
          titleColor: 'text-react-cyan',
          icon: <Info className="w-5 h-5 text-react-cyan shrink-0 mt-0.5" />,
          defaultTitle: 'Note',
        };
    }
  };

  const style = getStyles();

  return (
    <div className={`my-6 rounded-2xl border ${style.border} p-5 text-sm leading-relaxed shadow-sm`}>
      <div className="flex items-start gap-3">
        {style.icon}
        <div className="flex-1 min-w-0">
          <div className={`font-bold text-sm mb-1.5 ${style.titleColor}`}>
            {title || style.defaultTitle}
          </div>
          <div className="text-react-textMuted space-y-2 text-[13.5px]">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
};
