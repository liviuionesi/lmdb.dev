'use client';

import React, { useState } from 'react';
import { ClassDoc, MethodDoc } from '@/types/schema';
import { Copy, Check, Terminal, FileCode, Braces } from 'lucide-react';

interface CodeTabsProps {
  classDoc: ClassDoc;
  primaryMethod?: MethodDoc;
}

export const CodeTabs: React.FC<CodeTabsProps> = ({ classDoc, primaryMethod }) => {
  const [activeTab, setActiveTab] = useState<'java' | 'curl' | 'ts'>('java');
  const [copied, setCopied] = useState(false);

  const method = primaryMethod || classDoc.methods[0];
  const endpointPath = method?.httpPath || `/api/v1/${classDoc.name.toLowerCase().replace('controller', '')}s/1`;
  const httpMethod = method?.httpMethod || 'GET';

  const javaSnippet = `// 1. Inject or autowire the service / typed client
@Autowired
private ${classDoc.name} ${classDoc.name.charAt(0).toLowerCase() + classDoc.name.slice(1)};

// 2. Execute operation
${method ? `${method.returnType} result = ${classDoc.name.charAt(0).toLowerCase() + classDoc.name.slice(1)}.${method.name}(${method.parameters.map(p => p.type === 'Long' || p.type === 'int' ? '1' : `"${p.name}"`).join(', ')});` : `// Call methods on ${classDoc.name}`}`;

  const curlSnippet = `# Call endpoint directly via API Gateway (Port 8080)
curl -X ${httpMethod} "http://localhost:8080${endpointPath}" \\
     -H "Accept: application/json" \\
     -H "Authorization: Bearer <JWT_TOKEN>"`;

  const tsSnippet = `// React / Next.js / TypeScript Consumer
export async function fetch${classDoc.name.replace('Controller', '')}() {
  const res = await fetch(\`http://localhost:8080${endpointPath}\`, {
    headers: {
      'Accept': 'application/json',
      'Authorization': \`Bearer \${token}\`
    }
  });
  if (!res.ok) throw new Error('API Request Failed');
  return res.json();
}`;

  const getActiveCode = () => {
    switch (activeTab) {
      case 'curl':
        return curlSnippet;
      case 'ts':
        return tsSnippet;
      case 'java':
      default:
        return javaSnippet;
    }
  };

  const copyCode = () => {
    navigator.clipboard.writeText(getActiveCode());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="my-6 rounded-2xl border border-react-border bg-react-card overflow-hidden shadow-card">
      {/* Tab Navigation */}
      <div className="flex items-center justify-between px-3 pt-2 border-b border-react-border bg-react-bg/60">
        <div className="flex items-center gap-1">
          <button
            onClick={() => setActiveTab('java')}
            className={`flex items-center gap-2 px-3 py-2 text-xs font-semibold rounded-t-lg transition-colors border-b-2 ${
              activeTab === 'java'
                ? 'border-react-cyan text-react-cyan bg-react-card'
                : 'border-transparent text-react-textSubtle hover:text-react-text'
            }`}
          >
            <FileCode className="w-3.5 h-3.5" />
            <span>Java Client</span>
          </button>
          <button
            onClick={() => setActiveTab('curl')}
            className={`flex items-center gap-2 px-3 py-2 text-xs font-semibold rounded-t-lg transition-colors border-b-2 ${
              activeTab === 'curl'
                ? 'border-react-cyan text-react-cyan bg-react-card'
                : 'border-transparent text-react-textSubtle hover:text-react-text'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>cURL</span>
          </button>
          <button
            onClick={() => setActiveTab('ts')}
            className={`flex items-center gap-2 px-3 py-2 text-xs font-semibold rounded-t-lg transition-colors border-b-2 ${
              activeTab === 'ts'
                ? 'border-react-cyan text-react-cyan bg-react-card'
                : 'border-transparent text-react-textSubtle hover:text-react-text'
            }`}
          >
            <Braces className="w-3.5 h-3.5" />
            <span>TypeScript / React</span>
          </button>
        </div>

        <button
          onClick={copyCode}
          className="flex items-center gap-1.5 text-xs text-react-textSubtle hover:text-react-text px-2.5 py-1 rounded-lg hover:bg-react-card transition-colors mb-1"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-react-accentGreen" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>

      {/* Code Display */}
      <pre className="p-4 overflow-x-auto text-xs font-mono text-react-text bg-react-code leading-relaxed">
        <code>{getActiveCode()}</code>
      </pre>
    </div>
  );
};
