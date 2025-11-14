# Phase 5: Web Frontend (Next.js) - GitHub Issues

**Sprint:** 8-9 (2 weeks)  
**Focus:** Next.js 16 Web Application  
**Status:** Pending (After Phase 4)

---

## Epic

### Issue #21: [EPIC] Web Frontend Application

**Labels:** `epic`, `P1-high`, `sprint-8`, `frontend`, `web`

**Description:**
Implement modern, responsive web application using Next.js 16, React 19, TypeScript, and Material UI with server-side rendering and optimized performance.

**Business Value:**
Delivers production-ready web interface for movie discovery, user authentication, personalized recommendations, and interactive features.

**User Stories:**
- #22 - Setup Next.js Project Structure
- #23 - Implement Authentication & User Management
- #24 - Implement Movie Discovery & Search
- #25 - Implement Movie Details & Player
- #26 - Implement User Profile & Watchlist
- #27 - Implement AI Features (Chat & Recommendations)
- #28 - Implement Responsive Design & PWA

**Technical Stack:**
- Next.js 16.0.0 (App Router)
- React 19.0.2
- TypeScript 5.7.x
- Material UI 7.3.5
- TanStack Query 5.0.8
- Zustand 5.0.8
- Tailwind CSS 3.4.x

**Story Points:** 34  
**Target Sprint:** Sprint 8-9  
**Estimated Time:** 24-28 hours

---

## User Stories / Tasks

### Issue #22: [TASK] Setup Next.js Project Structure

**Labels:** `task`, `P0-critical`, `sprint-8`, `frontend`, `web`

**Description:**
Initialize Next.js 16 project with TypeScript, configure routing, state management, API integration, and development tools.

**Implementation Checklist:**
- [ ] Initialize Next.js 16 with TypeScript
- [ ] Configure App Router structure
- [ ] Set up Tailwind CSS
- [ ] Install and configure Material UI
- [ ] Set up TanStack Query
- [ ] Configure Zustand store
- [ ] Set up environment variables
- [ ] Configure ESLint and Prettier
- [ ] Set up testing (Jest + React Testing Library)
- [ ] Configure API client with Axios
- [ ] Add error boundary
- [ ] Create layout components
- [ ] Set up middleware for auth
- [ ] Configure PWA settings

**Project Structure:**
```
frontend/web-nextjs/
├── app/
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   └── register/page.tsx
│   ├── movies/
│   │   ├── [id]/page.tsx
│   │   └── page.tsx
│   ├── layout.tsx
│   └── page.tsx
├── components/
│   ├── common/
│   ├── movie/
│   └── user/
├── lib/
│   ├── api/
│   ├── hooks/
│   └── utils/
├── store/
│   ├── auth.ts
│   └── movies.ts
├── types/
│   └── index.ts
└── public/
```

**Acceptance Criteria:**
- [ ] Project structure created
- [ ] All dependencies installed
- [ ] TypeScript configured
- [ ] Routing working
- [ ] API client configured
- [ ] State management working
- [ ] Development tools configured
- [ ] Tests running

**Commands:**
```bash
npx create-next-app@latest web-nextjs --typescript
npm install @mui/material @emotion/react @emotion/styled
npm install @tanstack/react-query zustand
npm install axios zod react-hook-form
npm run dev
```

**Story Points:** 5  
**Estimated Time:** 3-4 hours

---

### Issue #23: [TASK] Implement Authentication & User Management

**Labels:** `task`, `P0-critical`, `sprint-8`, `frontend`, `web`

**Description:**
Implement complete authentication flow with JWT, protected routes, user registration, login, and profile management.

**Implementation Checklist:**
- [ ] Create login page
- [ ] Create registration page
- [ ] Implement JWT storage and refresh
- [ ] Create auth context/hook
- [ ] Implement protected route wrapper
- [ ] Create user profile page
- [ ] Implement profile editing
- [ ] Add password change functionality
- [ ] Create logout functionality
- [ ] Add form validation with Zod
- [ ] Implement error handling
- [ ] Add loading states
- [ ] Write component tests

**Key Components:**
```typescript
// app/(auth)/login/page.tsx
export default function LoginPage() {
  const { login, isLoading } = useAuth();
  const form = useForm<LoginSchema>();
  
  return (
    <Container>
      <LoginForm onSubmit={login} />
    </Container>
  );
}

// lib/hooks/useAuth.ts
export function useAuth() {
  const setUser = useAuthStore(state => state.setUser);
  
  const login = useMutation({
    mutationFn: (credentials) => authApi.login(credentials),
    onSuccess: (data) => {
      localStorage.setItem('token', data.token);
      setUser(data.user);
    }
  });
  
  return { login, logout, user };
}
```

**Acceptance Criteria:**
- [ ] Login working
- [ ] Registration working
- [ ] JWT stored securely
- [ ] Token refresh working
- [ ] Protected routes functional
- [ ] Profile management working
- [ ] Form validation working
- [ ] All tests passing

**Story Points:** 8  
**Estimated Time:** 6-7 hours

---

### Issue #24: [TASK] Implement Movie Discovery & Search

**Labels:** `task`, `P0-critical`, `sprint-8`, `frontend`, `web`

**Description:**
Implement movie browsing, filtering, search functionality with infinite scroll and advanced filters.

**Implementation Checklist:**
- [ ] Create movie grid component
- [ ] Implement movie card component
- [ ] Create search bar with autocomplete
- [ ] Implement genre filter
- [ ] Add sort options (rating, release date, popularity)
- [ ] Implement infinite scroll
- [ ] Create skeleton loading states
- [ ] Add error handling
- [ ] Implement caching strategy
- [ ] Optimize images (Next.js Image)
- [ ] Add SEO metadata
- [ ] Write component tests

**Key Components:**
```typescript
// app/movies/page.tsx
export default function MoviesPage() {
  const { 
    data, 
    fetchNextPage, 
    hasNextPage 
  } = useInfiniteMovies();
  
  return (
    <Container>
      <SearchBar />
      <FilterPanel />
      <MovieGrid movies={data?.pages.flatMap(p => p.results)} />
      <InfiniteScrollTrigger onVisible={fetchNextPage} />
    </Container>
  );
}

// components/movie/MovieCard.tsx
export function MovieCard({ movie }: { movie: Movie }) {
  return (
    <Card>
      <Image src={movie.posterPath} alt={movie.title} />
      <CardContent>
        <Typography variant="h6">{movie.title}</Typography>
        <Rating value={movie.voteAverage} />
      </CardContent>
    </Card>
  );
}
```

**Acceptance Criteria:**
- [ ] Movie grid rendering
- [ ] Search working
- [ ] Filters functional
- [ ] Infinite scroll working
- [ ] Loading states implemented
- [ ] Images optimized
- [ ] SEO implemented
- [ ] All tests passing
- [ ] Performance: < 3s load time

**Story Points:** 8  
**Estimated Time:** 6-7 hours

---

### Issue #25: [TASK] Implement Movie Details & Player

**Labels:** `task`, `P1-high`, `sprint-9`, `frontend`, `web`

**Description:**
Implement detailed movie view with trailer player, cast information, similar movies, and user interactions.

**Implementation Checklist:**
- [ ] Create movie details page
- [ ] Implement video player for trailers
- [ ] Display cast carousel
- [ ] Show similar movies section
- [ ] Add favorite/watchlist buttons
- [ ] Implement rating display
- [ ] Show production details
- [ ] Add share functionality
- [ ] Optimize SEO for movie pages
- [ ] Write component tests

**Story Points:** 8  
**Estimated Time:** 6-7 hours

---

### Issue #26: [TASK] Implement User Profile & Watchlist

**Labels:** `task`, `P1-high`, `sprint-9`, `frontend`, `web`

**Description:**
Implement user profile management, favorites list, and watchlist with drag-and-drop reordering.

**Implementation Checklist:**
- [ ] Create profile page
- [ ] Implement favorites grid
- [ ] Create watchlist with reordering
- [ ] Add profile editing
- [ ] Implement avatar upload
- [ ] Show watch history
- [ ] Add statistics dashboard
- [ ] Write component tests

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

### Issue #27: [TASK] Implement AI Features

**Labels:** `task`, `P2-medium`, `sprint-9`, `frontend`, `web`

**Description:**
Implement AI-powered recommendations, chat assistant, and voice search features.

**Implementation Checklist:**
- [ ] Create recommendations section
- [ ] Implement chat interface
- [ ] Add voice search button
- [ ] Implement conversation history
- [ ] Add typing indicators
- [ ] Create preference settings
- [ ] Write component tests

**Story Points:** 8  
**Estimated Time:** 6-7 hours

---

### Issue #28: [TASK] Implement Responsive Design & PWA

**Labels:** `task`, `P1-high`, `sprint-9`, `frontend`, `web`

**Description:**
Ensure responsive design across all devices and implement Progressive Web App features.

**Implementation Checklist:**
- [ ] Implement mobile-first design
- [ ] Test on tablet layouts
- [ ] Configure PWA manifest
- [ ] Add service worker
- [ ] Implement offline mode
- [ ] Add install prompt
- [ ] Optimize performance
- [ ] Run Lighthouse audits
- [ ] Write accessibility tests

**Acceptance Criteria:**
- [ ] Responsive on all devices
- [ ] PWA installable
- [ ] Offline mode working
- [ ] Lighthouse score > 90
- [ ] Accessibility score > 95
- [ ] All tests passing

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

## Definition of Done

✅ **Code Quality**
- TypeScript strict mode
- ESLint passing
- Prettier formatted
- No console errors

✅ **Testing**
- Component tests (80%+ coverage)
- Integration tests
- E2E tests (Playwright)

✅ **Performance**
- Lighthouse score > 90
- Core Web Vitals met
- Bundle size optimized

✅ **Accessibility**
- WCAG 2.1 AA compliant
- Keyboard navigation
- Screen reader compatible

---

**Phase Status:** Pending  
**Dependencies:** Phase 4 (Advanced Services)  
**Estimated Duration:** 2 weeks  
**Total Story Points:** 47

