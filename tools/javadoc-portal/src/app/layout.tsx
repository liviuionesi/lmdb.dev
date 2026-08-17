import type { Metadata } from 'next';
import './globals.css';
import { AppShell } from '@/components/layout/AppShell';
import { getProjectSchema } from '@/lib/schema-loader';

export const metadata: Metadata = {
  title: 'Filmpire Microservices - Exquisite Java Reference & API Portal',
  description:
    'Modern React.dev-styled API Reference, Javadoc, and Architecture portal for Java microservices.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const schema = getProjectSchema();

  return (
    <html lang="en" className="dark">
      <body>
        <AppShell schema={schema}>{children}</AppShell>
      </body>
    </html>
  );
}
