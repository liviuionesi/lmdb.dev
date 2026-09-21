import { fileURLToPath, URL } from 'node:url';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { defineConfig, coverageConfigDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';

const projectDir = fileURLToPath(new URL('.', import.meta.url));

/**
 * Development middleware to handle /api/wakeup requests directly in the Vite dev server.
 */
function wakeupDevPlugin() {
  return {
    name: 'lmdb-wakeup-dev-plugin',
    configureServer(server) {
      server.middlewares.use('/api/wakeup', (req, res) => {
        if (req.method === 'OPTIONS') {
          res.writeHead(200, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
          });
          res.end();
          return;
        }

        let body = '';
        req.on('data', (chunk) => {
          body += chunk;
        });
        req.on('end', () => {
          let parsed = {};
          try {
            parsed = JSON.parse(body || '{}');
          } catch {
            // Ignored
          }
          const cloud = parsed.cloud || 'azure';
          const repoRoot = path.resolve(projectDir, '../..');

          // Trigger backend start in background
          if (cloud === 'minikube' || cloud === 'tunnel') {
            spawn('/usr/bin/env', ['docker', 'compose', '-f', 'infrastructure/docker/docker-compose.yml', 'start'], {
              cwd: repoRoot,
              detached: true,
              stdio: 'ignore',
            }).unref();
          } else if (cloud === 'aws') {
            spawn('/bin/bash', ['infrastructure/scripts/start-aws.sh'], {
              cwd: repoRoot,
              detached: true,
              stdio: 'ignore',
            }).unref();
          } else {
            spawn('/bin/bash', ['infrastructure/scripts/start-azure.sh'], {
              cwd: repoRoot,
              detached: true,
              stdio: 'ignore',
            }).unref();
          }

          res.writeHead(200, {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
          });
          res.end(
            JSON.stringify({
              status: 'WAKING_UP',
              targetCloud: cloud,
              estimatedSeconds: 90,
              message: `Initiated backend start for ${cloud.toUpperCase()}`,
            }),
          );
        });
      });
    },
  };
}

/**
 * Vite build/dev-server configuration for the LMDB frontend, plus the
 * Vitest `test` block (#127) that replaces CRA's Jest setup.
 */
export default defineConfig({
  plugins: [react(), wakeupDevPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 3000,
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1000,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.js'],
    mockReset: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: [
        ...coverageConfigDefaults.exclude,
        'src/**/*.test.{js,jsx}',
        'src/setupTests.js',
        'src/test-utils/**',
        'src/index.jsx',
        'src/app/store.js',
        'src/assets/**',
        'src/components/index.js',
        'src/components/styles.js',
        'src/components/App.jsx',
      ],
      thresholds: {
        branches: 80,
        functions: 80,
        lines: 80,
        statements: 80,
      },
    },
  },
});
