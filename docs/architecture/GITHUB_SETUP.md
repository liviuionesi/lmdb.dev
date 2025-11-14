# GitHub Templates & Workflows Setup

## Overview

Complete GitHub configuration for issue tracking, pull requests, CI/CD automation, and dependency management.

## Components

### 1. Issue Templates ✅

Located in `.github/ISSUE_TEMPLATE/`:

#### Epic Template (`epic.md`)
- Large bodies of work broken into user stories
- Business value documentation
- Sprint planning and story points
- Definition of Done checklist

#### User Story Template (`user-story.md`)
- Standard "As a... I want... So that..." format
- Acceptance criteria with Given/When/Then
- Technical tasks breakdown
- API endpoints and database changes
- Test requirements

#### Task Template (`task.md`)
- Development tasks and technical work
- Implementation details and steps
- Files to create/modify list
- Testing requirements
- Time estimates and story points

#### Bug Report Template (`bug.md`)
- Bug description and reproduction steps
- Expected vs actual behavior
- Environment details
- Priority and severity levels
- Screenshots and logs

### 2. Pull Request Template ✅

Located at `.github/PULL_REQUEST_TEMPLATE.md`:

**Features:**
- Type of change classification
- Service(s) affected checklist
- Testing requirements (unit, integration, E2E)
- Code quality checklist
- Documentation checklist
- Security checklist
- Performance considerations
- Database changes
- Deployment notes
- Rollback plan

### 3. GitHub Actions Workflows ✅

Located in `.github/workflows/`:

#### Backend CI (`backend-ci.yml`)
**Triggers:**
- Push to main/develop branches
- Pull requests to main/develop
- Changes in backend/, build files, or workflow itself

**Jobs:**
- Setup Java 25 (Temurin distribution)
- Build all services (Gradle multi-module)
- Run all tests
- Generate JaCoCo coverage reports
- Upload test results and coverage as artifacts

**Configuration:**
- Gradle caching enabled
- Daemon disabled for CI
- Runs on ubuntu-latest

#### Frontend CI (`frontend-ci.yml`)
**Triggers:**
- Push to main/develop branches
- Pull requests to main/develop
- Changes in frontend/ or workflow itself

**Jobs:**

**Web (Next.js):**
- Setup Node.js 24
- Install dependencies (npm ci)
- Run linter
- Run type checking
- Run tests
- Build application
- Upload build artifacts

**Mobile (React Native):**
- Setup Node.js 24
- Install dependencies (npm ci)
- Run linter
- Run type checking
- Run tests

**Configuration:**
- npm caching enabled
- Runs on ubuntu-latest
- Continue on error for unconfigured scripts

#### Project Automation (`project-automation.yml`)
- Automated project board management
- Issue labeling and assignment
- PR status tracking

### 4. Dependabot Configuration ✅

Located at `.github/dependabot.yml`:

**Package Ecosystems Monitored:**

1. **Gradle (Backend)**
   - Weekly updates on Mondays at 9:00 AM
   - Grouped updates for Spring Boot, Spring Cloud, Spring AI
   - Separate group for test dependencies
   - Target branch: develop
   - Labels: dependencies, backend, automated

2. **npm (Next.js Web App)**
   - Weekly updates on Mondays at 9:00 AM
   - Grouped updates for React/Next.js and Material-UI
   - Separate group for dev dependencies
   - Target branch: develop
   - Labels: dependencies, frontend, web, automated

3. **npm (React Native Mobile App)**
   - Weekly updates on Mondays at 9:00 AM
   - Grouped updates for React Native and Expo
   - Separate group for dev dependencies
   - Target branch: develop
   - Labels: dependencies, frontend, mobile, automated

4. **GitHub Actions**
   - Monthly updates on Mondays at 9:00 AM
   - Keeps workflow actions up-to-date
   - Target branch: develop
   - Labels: dependencies, github-actions, automated

5. **Docker**
   - Monthly updates for Dockerfiles
   - Target branch: develop
   - Labels: dependencies, docker, automated

**Features:**
- Open PR limit: 10 per ecosystem (5 for Actions/Docker)
- Auto-reviewers configured
- Conventional commit messages
- Grouped dependencies for cleaner PRs

### 5. Branch Protection ⚠️

**Status:** Documented but not configured

**Reason:** Requires GitHub Pro or public repository (currently private on free tier)

**Documentation:** `.github/BRANCH_PROTECTION.md`

**Planned Rules for `main`:**
- Require 1 approval before merging
- Dismiss stale reviews on new commits
- Require status checks: Backend CI, Frontend CI
- Require conversation resolution
- No force pushes or deletions
- Linear history optional

**Script Available:** `.github/scripts/configure-branch-protection.sh`

**Workaround (Free Tier):**
- Always use feature branches
- Never commit directly to main
- Always get code review
- Wait for CI to pass
- Manual enforcement of best practices

### 6. Labels ✅

**Priority Labels:**
- `P0-critical` - Critical priority (red)
- `P1-high` - High priority (orange)
- `P2-medium` - Medium priority (yellow)
- `P3-low` - Low priority (green)

**Type Labels:**
- `epic` - Epic work items (purple)
- `user-story` - User stories (blue)
- `task` - Development tasks (teal)
- `bug` - Bug reports (red)
- `enhancement` - Feature requests (light blue)
- `documentation` - Documentation work (light blue)

**Area Labels:**
- `backend` - Backend services (yellow)
- `frontend` - Frontend applications (teal)
- `devops` - DevOps/Infrastructure (light teal)
- `infrastructure` - Infrastructure (green)

**Service Labels:**
- `movie-service`, `user-service`, `actor-service`, etc.
- `api-gateway`, `ai-service`

**Sprint Labels:**
- `sprint-0` - Setup sprint
- `sprint-1` - Development sprint 1
- etc.

**Other Labels:**
- `automated` - Auto-generated (e.g., Dependabot)
- `dependencies` - Dependency updates
- `help wanted` - Community help requested
- `good first issue` - Beginner-friendly

## Usage

### Creating Issues

Use issue templates from GitHub UI:
1. Go to Issues → New Issue
2. Choose appropriate template
3. Fill in required fields
4. Add appropriate labels
5. Assign to sprint/milestone

### Creating Pull Requests

1. Create feature branch from main:
   ```bash
   git checkout -b feature/description
   ```

2. Make changes and commit:
   ```bash
   git add .
   git commit -m "feat(service): description"
   ```

3. Push and create PR:
   ```bash
   git push origin feature/description
   gh pr create --fill
   ```

4. PR template will auto-populate - fill in all sections

### Monitoring CI/CD

**View Workflow Runs:**
```bash
gh run list
gh run view <run-id>
gh run watch
```

**Check Status:**
- GitHub Actions tab in repository
- PR status checks
- Commit status indicators

### Managing Dependencies

**Dependabot PRs:**
- Auto-created weekly/monthly
- Review and approve grouped updates
- Test locally if needed
- Merge when CI passes

**Manual Dependency Updates:**
```bash
# Backend
./gradlew dependencyUpdates

# Frontend
cd frontend/web-nextjs && npm outdated
cd frontend/mobile-react-native && npm outdated
```

## Verification

### ✅ Task #4 Acceptance Criteria

- [x] All issue templates available when creating issues
- [x] PR template appears for new PRs
- [x] CI workflows configured for backend and frontend
- [x] Backend CI runs on push to main/develop
- [x] Frontend CI runs on push to main/develop
- [x] Labels created and documented
- [x] Dependabot configured for all package ecosystems
- [x] Branch protection documented (awaiting Pro/public repo)

### Test Workflows

**Backend CI:**
```bash
# Trigger by pushing to backend
git checkout -b test/backend-ci
touch backend/movie-service/test.txt
git add . && git commit -m "test: trigger backend CI"
git push origin test/backend-ci
gh pr create --title "Test Backend CI" --body "Testing workflow"
```

**Frontend CI:**
```bash
# Trigger by pushing to frontend
git checkout -b test/frontend-ci
touch frontend/web-nextjs/test.txt
git add . && git commit -m "test: trigger frontend CI"
git push origin test/frontend-ci
gh pr create --title "Test Frontend CI" --body "Testing workflow"
```

**Check Results:**
```bash
gh run list --workflow backend-ci.yml
gh run list --workflow frontend-ci.yml
```

## Maintenance

### Updating Workflows

1. Edit workflow files in `.github/workflows/`
2. Test in feature branch first
3. Create PR with changes
4. Verify workflow runs successfully
5. Merge when validated

### Adding New Services

When adding new services, update:
- Dependabot configuration (if new package ecosystem)
- CI workflows (if special build requirements)
- Issue templates (if new service labels needed)

### Updating Labels

```bash
# Create new label
gh label create "new-label" --color "hexcolor" --description "desc"

# Update existing label
gh label edit "label-name" --color "newhex" --description "new desc"

# Delete label
gh label delete "old-label"
```

## Files Created

```
.github/
├── ISSUE_TEMPLATE/
│   ├── bug.md                           ✅
│   ├── epic.md                          ✅
│   ├── task.md                          ✅
│   └── user-story.md                    ✅
├── scripts/
│   └── configure-branch-protection.sh   ✅
├── workflows/
│   ├── backend-ci.yml                   ✅ (Updated)
│   ├── frontend-ci.yml                  ✅ (New)
│   └── project-automation.yml           ✅
├── BRANCH_PROTECTION.md                 ✅ (New)
├── PULL_REQUEST_TEMPLATE.md             ✅
└── dependabot.yml                       ✅ (New)
```

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Dependabot Configuration](https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/configuration-options-for-the-dependabot.yml-file)
- [Branch Protection Rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [Issue Templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)

---

**Status:** ✅ Complete  
**Task:** #4 - Create GitHub Templates & Workflows  
**Date:** 2025-11-14

