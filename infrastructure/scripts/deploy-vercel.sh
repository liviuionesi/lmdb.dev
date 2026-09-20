#!/usr/bin/env bash
# LMDB Microservices - 1-Step Automated Vercel Deployment Script
# Automatically builds the React/Vite frontend and deploys it to Vercel with zero manual dashboard steps.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../../frontend/lmdb"

echo "=================================================="
echo "  🚀 LMDB 1-Step Automated Vercel Deployment"
echo "=================================================="
echo ""

cd "$FRONTEND_DIR"

if [ -z "${VITE_TMDB_KEY:-}" ]; then
  echo "⚠️  VITE_TMDB_KEY is not set in the environment. The deployed build"
  echo "   won't be able to reach the TMDB API without it."
fi

# 1. Verify build dependencies
if [ ! -d "node_modules" ]; then
  echo "📦 Installing frontend dependencies..."
  npm install
fi

# 2. Build production bundle
echo "🔨 Compiling production frontend bundle..."
npm run build

echo "✓ Frontend bundle built successfully in dist/"

# 3. Deploy to Vercel, passing through the two Vite build-time variables the
# deployed app reads (frontend/lmdb/src/services/TMDB.js, src/utils/apiUrl.js)
# as --build-env so Vercel's own cloud build receives them without requiring
# a one-time manual entry in the Vercel dashboard's environment variables page.
BUILD_ENV_ARGS=()
[ -n "${VITE_API_URL:-}" ] && BUILD_ENV_ARGS+=(--build-env "VITE_API_URL=$VITE_API_URL")
[ -n "${VITE_TMDB_KEY:-}" ] && BUILD_ENV_ARGS+=(--build-env "VITE_TMDB_KEY=$VITE_TMDB_KEY")

echo ""
echo "🚀 Deploying to Vercel production..."
if [ -n "${VERCEL_TOKEN:-}" ]; then
  npx -y vercel --prod --yes --token "$VERCEL_TOKEN" "${BUILD_ENV_ARGS[@]}"
else
  echo "ℹ️ Running Vercel CLI in interactive/authenticated mode..."
  npx -y vercel --prod --yes "${BUILD_ENV_ARGS[@]}"
fi

echo ""
echo "=================================================="
echo "  🎉 Vercel Deployment Complete!"
echo "=================================================="
echo "  Your frontend is live on Vercel and configured"
echo "  to route API requests to https://api.lmdb.dev"
echo "=================================================="
