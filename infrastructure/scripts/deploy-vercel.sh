#!/usr/bin/env bash
# LMDB Microservices - 1-Step Automated Vercel Deployment Script
# Automatically builds the React/Vite frontend and deploys it to Vercel with zero manual dashboard steps.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../../frontend/filmpire"

echo "=================================================="
echo "  🚀 LMDB 1-Step Automated Vercel Deployment"
echo "=================================================="
echo ""

cd "$FRONTEND_DIR"

# 1. Verify build dependencies
if [ ! -d "node_modules" ]; then
  echo "📦 Installing frontend dependencies..."
  npm install
fi

# 2. Build production bundle
echo "🔨 Compiling production frontend bundle..."
npm run build

echo "✓ Frontend bundle built successfully in dist/"

# 3. Deploy to Vercel
echo ""
echo "🚀 Deploying to Vercel production..."
if [ -n "${VERCEL_TOKEN:-}" ]; then
  npx -y vercel --prod --yes --token "$VERCEL_TOKEN"
else
  echo "ℹ️ Running Vercel CLI in interactive/authenticated mode..."
  npx -y vercel --prod --yes
fi

echo ""
echo "=================================================="
echo "  🎉 Vercel Deployment Complete!"
echo "=================================================="
echo "  Your frontend is live on Vercel and configured"
echo "  to route API requests to https://api.lmdb.dev"
echo "=================================================="
