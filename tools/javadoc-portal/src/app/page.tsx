import React from 'react';
import Link from 'next/link';
import { getAllModules, getProjectSchema } from '@/lib/schema-loader';
import { Callout } from '@/components/reference/Callout';
import {
  Server,
  Code2,
  Boxes,
  ArrowRight,
  Sparkles,
  ShieldCheck,
  Zap,
  Activity,
  Layers,
} from 'lucide-react';

export default function HomePage() {
  const schema = getProjectSchema();
  const modules = getAllModules();

  return (
    <div className="max-w-5xl mx-auto space-y-12 pb-16">
      {/* Hero Section */}
      <div className="space-y-4 pt-4 border-b border-react-border pb-10">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-react-cyan/10 border border-react-cyan/30 text-xs font-semibold text-react-cyan">
          <Sparkles className="w-3.5 h-3.5" />
          <span>Next.js Universal Java Documentation Service</span>
        </div>
        <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-react-text">
          {schema.projectName}{' '}
          <span className="text-react-cyan">API Reference</span>
        </h1>
        <p className="text-lg text-react-textMuted max-w-3xl leading-relaxed">
          {schema.description}. A unified, interactive developer portal providing deep class-level documentation,
          Spring Boot endpoints, parameter specifications, and hyperlinked source code.
        </p>

        {/* Quick Stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-4">
          <div className="p-4 rounded-xl bg-react-card border border-react-border">
            <div className="text-2xl font-bold text-react-cyan">{modules.length}</div>
            <div className="text-xs text-react-textSubtle uppercase tracking-wider font-medium mt-1">Microservices</div>
          </div>
          <div className="p-4 rounded-xl bg-react-card border border-react-border">
            <div className="text-2xl font-bold text-react-text">{schema.totalClasses}</div>
            <div className="text-xs text-react-textSubtle uppercase tracking-wider font-medium mt-1">Documented Types</div>
          </div>
          <div className="p-4 rounded-xl bg-react-card border border-react-border">
            <div className="text-2xl font-bold text-react-accentGreen">Spring Boot 4</div>
            <div className="text-xs text-react-textSubtle uppercase tracking-wider font-medium mt-1">Framework</div>
          </div>
          <div className="p-4 rounded-xl bg-react-card border border-react-border">
            <div className="text-2xl font-bold text-react-accentYellow">JDK 21 LTS</div>
            <div className="text-xs text-react-textSubtle uppercase tracking-wider font-medium mt-1">Runtime</div>
          </div>
        </div>
      </div>

      {/* Architectural Lead Developer Callout */}
      <Callout type="tip" title="Lead Developer Architecture Contract">
        All microservices follow strict bounded context discipline. Services communicate over HTTP / REST with client-side rate limiting (<code>Bucket4j</code>) and resilience (<code>Resilience4j</code>). Responses are enveloped in the standardized <code>ApiResponse&lt;T&gt;</code> and <code>HalResource&lt;T&gt;</code> formats defined in <code>shared-library</code>.
      </Callout>

      {/* Microservices Grid */}
      <div className="space-y-6">
        <div>
          <h2 className="text-2xl font-bold text-react-text flex items-center gap-2">
            <Layers className="w-6 h-6 text-react-cyan" />
            <span>Microservice Catalog</span>
          </h2>
          <p className="text-sm text-react-textMuted mt-1">
            Select any microservice below to browse its controllers, services, repositories, and DTO contracts.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {modules.map((mod) => {
            const firstClass = mod.classes.find(c => c.category.includes('Controller')) || mod.classes[0];
            const targetUrl = firstClass ? `/reference/${mod.id}/${firstClass.name}` : '#';

            return (
              <div
                key={mod.id}
                className="group rounded-2xl border border-react-border bg-react-card/60 hover:bg-react-card hover:border-react-cyan/50 transition-all p-6 flex flex-col justify-between shadow-card hover:shadow-cyan"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <div className="w-9 h-9 rounded-xl bg-react-bg border border-react-borderSubtle flex items-center justify-center text-react-cyan group-hover:scale-110 transition-transform">
                        <Server className="w-4 h-4" />
                      </div>
                      <h3 className="text-lg font-bold text-react-text group-hover:text-react-cyan transition-colors">
                        {mod.name}
                      </h3>
                    </div>
                    {mod.port !== 'N/A' && (
                      <span className="text-xs font-mono font-semibold px-2 py-1 rounded-md bg-react-bg border border-react-border text-react-cyan">
                        Port {mod.port}
                      </span>
                    )}
                  </div>

                  <p className="text-xs text-react-textMuted leading-relaxed">
                    {mod.description}
                  </p>

                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {mod.classes.slice(0, 4).map((c) => (
                      <span
                        key={c.id}
                        className="text-[11px] font-mono px-2 py-0.5 rounded bg-react-bg/80 border border-react-borderSubtle text-react-textSubtle"
                      >
                        {c.name}
                      </span>
                    ))}
                    {mod.classes.length > 4 && (
                      <span className="text-[11px] font-mono px-1.5 py-0.5 text-react-cyan">
                        +{mod.classes.length - 4} more
                      </span>
                    )}
                  </div>
                </div>

                <div className="pt-5 border-t border-react-borderSubtle mt-5 flex items-center justify-between text-xs">
                  <span className="text-react-textSubtle font-medium">
                    {mod.classes.length} documented classes
                  </span>
                  <Link
                    href={targetUrl}
                    className="inline-flex items-center gap-1 font-semibold text-react-cyan group-hover:translate-x-1 transition-transform"
                  >
                    <span>Browse Reference</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Universal Service Usage Callout */}
      <Callout type="deep-dive" title="Using This Portal with ANY External Java Project">
        This documentation service is 100% universal and decoupled from this specific repository. To generate and serve documentation for any external Spring Boot, Quarkus, or Java library project:
        <pre className="mt-2 p-3 rounded-xl bg-react-code font-mono text-xs text-react-cyan">
          npx extract-java-docs --src /path/to/any/java/project --out ./docs-schema.json
        </pre>
      </Callout>
    </div>
  );
}
