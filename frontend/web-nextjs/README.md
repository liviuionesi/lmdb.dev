# Filmpire Web Application (Next.js 16)

Modern movie discovery platform built with Next.js 16, React 19, and Material UI.

## Tech Stack

- **Next.js**: 16.0.0
- **React**: 19.0.2
- **TypeScript**: 5.7.2
- **Material UI**: 7.3.5
- **TanStack Query**: 5.0.8
- **Zustand**: 5.0.8

## Getting Started

```bash
# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build

# Start production server
npm start
```

## Environment Variables

Create a `.env.local` file:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Project Structure

```
web-nextjs/
├── app/              # Next.js App Router
├── components/       # React components
├── lib/             # Utilities and API clients
├── hooks/           # Custom React hooks
├── types/           # TypeScript types
└── public/          # Static assets
```

