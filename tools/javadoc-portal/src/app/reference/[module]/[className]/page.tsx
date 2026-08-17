import React from 'react';
import { notFound } from 'next/navigation';
import Link from 'next/link';
import { getAllModules, getClassById } from '@/lib/schema-loader';
import { MethodSignature } from '@/components/reference/MethodSignature';
import { Callout } from '@/components/reference/Callout';
import { CodeTabs } from '@/components/reference/CodeTabs';
import { SourceViewer } from '@/components/reference/SourceViewer';
import { TableOfContents } from '@/components/layout/TableOfContents';
import {
  Copy,
  Check,
  ChevronRight,
  Code2,
  FileCode2,
  Boxes,
  Zap,
  Radio,
  BookOpen,
  Terminal,
} from 'lucide-react';
import { CopyImportButton } from './CopyImportButton';

interface PageProps {
  params: {
    module: string;
    className: string;
  };
}

export function generateStaticParams() {
  const modules = getAllModules();
  const params: { module: string; className: string }[] = [];

  modules.forEach((mod) => {
    mod.classes.forEach((cls) => {
      params.push({
        module: mod.id,
        className: cls.name,
      });
    });
  });

  return params;
}

export default function ReferencePage({ params }: PageProps) {
  const resolved = getClassById(params.module, params.className);

  if (!resolved) {
    notFound();
  }

  const { module: mod, classDoc } = resolved;

  // Build TOC items
  const tocItems = [
    { id: 'overview', title: 'Overview', level: 2 },
  ];

  if (classDoc.methods.length > 0) {
    tocItems.push({ id: 'methods-reference', title: 'Method Reference', level: 2 });
    classDoc.methods.forEach((m) => {
      tocItems.push({
        id: `method-${m.name}`,
        title: `${m.name}()`,
        level: 3,
      });
    });
  }

  tocItems.push(
    { id: 'code-examples', title: 'Usage & Examples', level: 2 },
    { id: 'source-code', title: 'Java Source Code', level: 2 }
  );

  return (
    <div className="flex gap-10">
      {/* Center Main Content */}
      <div className="flex-1 min-w-0 max-w-4xl space-y-10 pb-20">
        {/* Breadcrumb Navigation */}
        <div className="flex items-center gap-1.5 text-xs text-react-textSubtle font-medium">
          <Link href="/" className="hover:text-react-text transition-colors">
            Modules
          </Link>
          <ChevronRight className="w-3.5 h-3.5" />
          <span className="text-react-cyan">{mod.name}</span>
          <ChevronRight className="w-3.5 h-3.5" />
          <span>{classDoc.category}</span>
          <ChevronRight className="w-3.5 h-3.5" />
          <span className="text-react-text font-mono font-semibold">{classDoc.name}</span>
        </div>

        {/* Header Title Section */}
        <div id="overview" className="space-y-4 scroll-mt-24 border-b border-react-border pb-8">
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="text-xs font-mono font-bold uppercase tracking-wider px-2.5 py-1 rounded-md bg-react-cyan/15 border border-react-cyan/30 text-react-cyan">
              {classDoc.kind}
            </span>
            <span className="text-xs font-mono px-2.5 py-1 rounded-md bg-react-card border border-react-border text-react-textSubtle">
              {classDoc.package}
            </span>
            <CopyImportButton importStatement={`import ${classDoc.package}.${classDoc.name};`} />
          </div>

          <h1 className="text-3xl sm:text-4xl font-extrabold text-react-text font-mono tracking-tight">
            {classDoc.name}
          </h1>

          {/* Spring Annotations Badges */}
          {classDoc.annotations.length > 0 && (
            <div className="flex flex-wrap gap-2 pt-1">
              {classDoc.annotations.map((annot) => (
                <span
                  key={annot.name}
                  className="text-xs font-mono font-semibold px-2.5 py-0.5 rounded-lg bg-react-code border border-react-border text-react-cyan"
                >
                  {annot.name}
                  {annot.value && <span className="text-react-textMuted text-[11px] ml-1 font-normal">({annot.value})</span>}
                </span>
              ))}
            </div>
          )}

          {/* Javadoc Description */}
          <div className="text-base text-react-textMuted leading-relaxed pt-2">
            <p>{classDoc.description || classDoc.summary}</p>
          </div>
        </div>

        {/* Lead Dev Architectural Callout */}
        <Callout type="tip" title={`${classDoc.name} Architecture & Design Patterns`}>
          This {classDoc.kind} is encapsulated within <code>{mod.name}</code> under the <code>{classDoc.package}</code> package boundary. All external invocations are routed through standardized Spring Boot MVC / REST components.
        </Callout>

        {/* Method Reference Section */}
        <div id="methods-reference" className="space-y-6 scroll-mt-24">
          <div className="border-b border-react-border pb-3">
            <h2 className="text-2xl font-bold text-react-text flex items-center gap-2">
              <Code2 className="w-6 h-6 text-react-cyan" />
              <span>Methods &amp; Operations Reference</span>
            </h2>
            <p className="text-sm text-react-textMuted mt-1">
              Detailed breakdown of method signatures, parameters, return types, and exceptions.
            </p>
          </div>

          {classDoc.methods.length === 0 ? (
            <p className="text-sm text-react-textSubtle italic">
              No public methods documented on this type.
            </p>
          ) : (
            <div className="space-y-8">
              {classDoc.methods.map((method) => (
                <MethodSignature key={method.name} method={method} />
              ))}
            </div>
          )}
        </div>

        {/* Code Examples Section */}
        <div id="code-examples" className="space-y-4 scroll-mt-24 pt-4">
          <div className="border-b border-react-border pb-3">
            <h2 className="text-2xl font-bold text-react-text flex items-center gap-2">
              <Terminal className="w-6 h-6 text-react-cyan" />
              <span>Usage &amp; Code Examples</span>
            </h2>
            <p className="text-sm text-react-textMuted mt-1">
              Multi-language code snippets for calling and integrating with this class.
            </p>
          </div>

          <CodeTabs classDoc={classDoc} />
        </div>

        {/* Source Code Section */}
        <div className="pt-4">
          <SourceViewer
            sourceCode={classDoc.sourceCode}
            filePath={classDoc.filePath}
          />
        </div>
      </div>

      {/* Sticky Right-Side TOC */}
      <TableOfContents items={tocItems} />
    </div>
  );
}
