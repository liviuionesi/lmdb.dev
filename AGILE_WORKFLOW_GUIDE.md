# Agile Workflow Guide - Filmpire Microservices

## Complete Setup: From Zero to First Sprint

This guide provides the **complete workflow** for agile development with GitHub Projects, integrated with Cursor IDE.

---

## 🎯 Overview

We're using **GitHub Projects** (free, integrated) for:
- ✅ Kanban board with automated workflows
- ✅ Issue tracking linked to code
- ✅ Automated PR → Issue → Commit tracking
- ✅ Sprint planning and backlog management
- ✅ Zero context switching (everything in GitHub)

---

## 📋 Phase 1: GitHub Repository Setup

### Step 1: Install GitHub CLI (if not installed)

```bash
# Fedora/RHEL
sudo dnf install gh

# Verify installation
gh --version
gh auth login  # Follow prompts
```

### Step 2: Create Repository

```bash
cd /home/liviu/Desktop/filmpire-microservices

# Initialize git
git init
git add .
git commit -m "chore: initial project structure and documentation"

# Create GitHub repository
gh repo create filmpire-microservices \
  --public \
  --source=. \
  --remote=origin \
  --description="Enterprise microservices platform for movie discovery"

# Push to GitHub
git branch -M main
git push -u origin main
```

### Step 3: Create Labels

```bash
# Run the automated script
./.github/scripts/create-phase1-issues.sh

# Or manually:
gh label create "P0-critical" --color "d73a4a" --description "Critical priority"
gh label create "P1-high" --color "ff6b6b" --description "High priority"
gh label create "P2-medium" --color "ffd93d" --description "Medium priority"
gh label create "P3-low" --color "6bcf7f" --description "Low priority"
gh label create "epic" --color "5319e7" --description "Epic"
gh label create "task" --color "008672" --description "Task"
gh label create "sprint-0" --color "e4e669" --description "Sprint 0 - Setup"
# ... (see script for complete list)
```

---

## 📊 Phase 2: GitHub Projects Setup

### Step 1: Create Project Board

**Via Web (Recommended):**
1. Go to: https://github.com/YOUR_USERNAME?tab=projects
2. Click "New project"
3. Choose "Board" view
4. Name: "Filmpire Microservices"

**Via CLI:**
```bash
# Note: Projects beta CLI might require additional setup
gh project create --owner @me --title "Filmpire Microservices"
```

### Step 2: Configure Board Columns

Create these columns (in order):

| Column | Description | Automation |
|--------|-------------|------------|
| 📋 **Backlog** | All planned work | Auto-add new issues |
| 🎯 **Sprint Ready** | Ready to start | Move here during sprint planning |
| 🚧 **In Progress** | Currently working | Auto-move when PR opened |
| 👀 **In Review** | Awaiting review | Auto-move when PR ready for review |
| ✅ **Done** | Completed | Auto-move when PR merged |

### Step 3: Add Custom Fields

In project settings, add these fields:
- **Priority** (Single select): P0, P1, P2, P3
- **Type** (Single select): Epic, Story, Task, Bug
- **Service** (Single select): api-gateway, movie-service, user-service, etc.
- **Sprint** (Single select): Sprint 0, Sprint 1, Sprint 2...
- **Story Points** (Number): 1, 2, 3, 5, 8, 13

### Step 4: Configure Automation

In project settings → Workflows:

1. **Auto-add items**
   - When: Issue or PR created
   - Then: Add to Backlog

2. **Set status**
   - When: PR opened
   - Then: Status = In Review

3. **Auto-close**
   - When: PR merged
   - Then: Status = Done, Close issue

---

## 🎫 Phase 3: Create Initial Issues

```bash
# Run the automated script
./.github/scripts/create-phase1-issues.sh
```

This creates:
- **Epic #1**: Project Setup Phase
- **Task #2**: Initialize Project Structure
- **Task #3**: Setup Gradle Multi-Module Build
- **Task #4**: Create GitHub Templates & Workflows
- **Task #5**: Setup Docker Compose Infrastructure

All issues automatically appear in your project board!

---

## 🔄 Development Workflow (The Core Process)

### Step 1: Pick a Task

```bash
# View all issues
gh issue list

# Pick one (e.g., #2)
gh issue view 2

# See full details
gh issue view 2 --web
```

### Step 2: Create Branch & Start Work

```bash
# Create branch from issue (auto-naming)
gh issue develop 2 --checkout

# This creates: 2-initialize-project-structure
# And moves issue to "In Progress" (if configured)
```

### Step 3: Implement in Cursor

Open Cursor IDE and work on the task:
- I'll help you write the code
- I'll help you write tests (TDD approach)
- I'll verify the implementation

```bash
# Example: Task #2 - Create project structure
mkdir -p backend/{movie-service,user-service,actor-service}/src/main/java
mkdir -p backend/{movie-service,user-service,actor-service}/src/main/resources
mkdir -p backend/{movie-service,user-service,actor-service}/src/test/java
# ... etc
```

### Step 4: Test Your Changes

```bash
# For Gradle tasks
./gradlew clean build

# For specific service
./gradlew :backend:movie-service:test

# Check coverage
./gradlew jacocoTestReport
```

### Step 5: Commit with Conventional Commits

```bash
git add .
git commit -m "feat(setup): initialize project structure

- Created all 8 backend service directories
- Added standard Java package structure
- Created frontend directories
- Added README.md for each service
- Configured .gitignore

Closes #2"

# Push to your branch
git push origin 2-initialize-project-structure
```

### Step 6: Create Pull Request

```bash
# Create PR (auto-links to issue via "Closes #2")
gh pr create \
  --title "feat(setup): initialize project structure" \
  --body "Closes #2

## Changes
- Created all 8 backend service directories
- Added standard Java package structure
- Created frontend and infrastructure directories

## Testing
- Verified directory structure with tree command
- All directories match ARCHITECTURE.md spec" \
  --assignee @me

# Or use interactive mode
gh pr create
```

**Project board automatically moves issue to "In Review"!**

### Step 7: CI/CD Runs Automatically

GitHub Actions workflows run:
- ✅ Build check
- ✅ Tests
- ✅ Linting
- ✅ Coverage report

### Step 8: Code Review

Wait for review (or self-review for personal project):

```bash
# Check PR status
gh pr status

# View PR in browser
gh pr view --web

# If changes requested, make updates and push
git add .
git commit -m "fix: address review comments"
git push
```

### Step 9: Merge PR

```bash
# After approval, merge (squash recommended)
gh pr merge --squash --delete-branch

# Or via web interface
```

**Project board automatically:**
- ✅ Moves issue to "Done"
- ✅ Closes the issue
- ✅ Archives the card

---

## 🏃 Sprint Planning Workflow

### Start of Sprint (e.g., Sprint 0)

**Step 1: Review Backlog**
```bash
# List all backlog issues
gh issue list --label "sprint-0"
```

**Step 2: Move to Sprint Ready**
In the project board:
1. Drag issues from "Backlog" to "Sprint Ready"
2. Ensure they're prioritized (P0 first)
3. Assign story points
4. Assign to team members

**Step 3: Create Milestone**
```bash
gh milestone create "Sprint 0" \
  --due-date "2025-11-21" \
  --description "Initial project setup"

# Add issues to milestone
gh issue edit 2 --milestone "Sprint 0"
gh issue edit 3 --milestone "Sprint 0"
# ... etc
```

### During Sprint (Daily Workflow)

**Morning Standup:**
```bash
# Check what's in progress
gh issue list --assignee @me --label "sprint-0" --state open

# View project board
gh project list
```

**Pick Next Task:**
```bash
# After completing one task, pick the next
gh issue list --label "sprint-0,P0-critical" --state open
gh issue develop <NUMBER> --checkout
```

### End of Sprint

**Step 1: Review Completed Work**
```bash
# List completed issues
gh issue list --milestone "Sprint 0" --state closed
```

**Step 2: Move Incomplete Items**
- In project board, move unfinished items back to backlog
- Update priorities if needed

**Step 3: Sprint Retrospective**
- What went well?
- What didn't go well?
- Action items for next sprint

**Step 4: Close Milestone**
```bash
gh milestone close "Sprint 0"
```

---

## 📈 Advanced Workflows

### Creating New Issues During Development

```bash
# Found a bug while working?
gh issue create \
  --title "[BUG] Gradle build fails on Java 24" \
  --label "bug,P1-high,backend" \
  --body "Description of bug..."

# Need a sub-task?
gh issue create \
  --title "[TASK] Add Lombok configuration" \
  --label "task,P2-medium,backend" \
  --body "Related to #3"
```

### Linking Multiple Issues

```bash
# In PR description or commit:
# "Fixes #2, Closes #3, Related to #1"
```

### Viewing Project Analytics

```bash
# Issues by milestone
gh issue list --milestone "Sprint 0" --json number,title,state --jq '.[] | "\(.number): \(.title) [\(.state)]"'

# Issues by assignee
gh issue list --assignee @me --state all

# Recent activity
gh issue list --limit 10
```

### Working on Multiple Tasks

```bash
# Save current work
git stash

# Switch to different issue
gh issue develop 5 --checkout

# Work on it...

# Return to original work
git checkout 2-initialize-project-structure
git stash pop
```

---

## 🎨 Cursor IDE Integration

### My Role as AI Assistant

When you're working on a task in Cursor:

**1. Understanding the Task**
```
You: "I'm starting on issue #2"
Me: *reads issue via gh CLI*
     "Got it! Task #2 is to initialize project structure.
     Let me create all the directories and README files for you."
```

**2. Implementing the Solution**
```
Me: *Creates all necessary files*
    *Writes tests first (TDD)*
    *Implements the code*
    *Verifies it works*
```

**3. Quality Checks**
```
Me: *Runs linter*
    *Runs tests*
    *Checks coverage*
    *Validates against acceptance criteria*
```

**4. Git Operations**
```
Me: *Creates meaningful commits*
    *Pushes to branch*
    *Creates PR with proper description*
```

**5. Continuous Feedback**
```
Me: "Build passing ✅"
    "Tests: 92% coverage ✅"
    "Ready for review ✅"
```

### Example Session

```
You: "Start task #3 - setup gradle"
Me: 
  1. gh issue develop 3 --checkout
  2. Create settings.gradle with proper order
  3. Create build.gradle with Java 25 config
  4. Create gradle.properties
  5. ./gradlew clean build
  6. ✅ All tests pass
  7. git commit -m "feat(build): setup gradle multi-module"
  8. gh pr create
  9. Issue automatically moves to "In Review"
  
You: "Perfect! Now task #4"
Me: *Repeats process for next task*
```

---

## 📊 Tracking Progress

### View Current Sprint

```bash
# All sprint issues
gh issue list --label "sprint-0"

# Just in-progress
gh issue list --label "sprint-0" --assignee @me --state open

# View in project board
gh project list
# Then open URL in browser
```

### Burndown Chart (Manual)

Track daily:
- Total story points in sprint: 16
- Day 1: 16 remaining
- Day 2: 13 remaining (completed 3 points)
- Day 3: 8 remaining (completed 5 points)
- etc.

### Velocity Tracking

After each sprint:
- Sprint 0: Completed 16 points
- Sprint 1: Completed 18 points
- Sprint 2: Completed 20 points
- Average velocity: 18 points/sprint

---

## 🎯 Next Steps: Start Sprint 0!

### Day 1-2: Setup Foundation

```bash
# Task #2: Project Structure (3 points, 2-3 hours)
gh issue develop 2 --checkout
# ... work with me in Cursor ...
gh pr create && gh pr merge

# Task #3: Gradle Build (5 points, 4-5 hours)
gh issue develop 3 --checkout
# ... work with me in Cursor ...
gh pr create && gh pr merge
```

### Day 3-4: DevOps & Infrastructure

```bash
# Task #4: GitHub Workflows (3 points, 2-3 hours)
gh issue develop 4 --checkout
# ... work with me in Cursor ...
gh pr create && gh pr merge

# Task #5: Docker Compose (5 points, 4-5 hours)
gh issue develop 5 --checkout
# ... work with me in Cursor ...
gh pr create && gh pr merge
```

### Day 5: Review & Close Sprint

```bash
# Verify everything works
./gradlew clean build
docker-compose up -d

# Close milestone
gh milestone close "Sprint 0"

# Plan Sprint 1
# (Infrastructure services: Eureka, Config, Gateway)
```

---

## 🚀 Benefits of This Workflow

1. **Full Traceability**
   - Every code change links to an issue
   - Every issue links to a PR
   - Complete audit trail

2. **Automated Updates**
   - Issue status updates automatically
   - Project board stays current
   - No manual card moving

3. **Code Quality**
   - CI/CD runs on every PR
   - Tests required before merge
   - Coverage tracked

4. **Team Collaboration** (when ready)
   - Clear task ownership
   - Review process built-in
   - Communication in PR comments

5. **Portfolio Value**
   - Professional project management
   - Demonstrates agile practices
   - Shows enterprise workflow knowledge

---

## 📚 Reference

### Useful Commands

```bash
# Issues
gh issue list
gh issue view <NUMBER>
gh issue create
gh issue develop <NUMBER> --checkout
gh issue close <NUMBER>

# Pull Requests
gh pr create
gh pr list
gh pr view <NUMBER>
gh pr merge <NUMBER> --squash
gh pr status

# Project
gh project list
gh project view <NUMBER>

# Repository
gh repo view --web
gh repo clone <OWNER>/<REPO>

# Workflow
gh run list
gh run view <RUN_ID>
gh run watch
```

### File Structure Created

```
.github/
├── ISSUE_TEMPLATE/
│   ├── epic.md
│   ├── user-story.md
│   ├── task.md
│   └── bug.md
├── PULL_REQUEST_TEMPLATE.md
├── PROJECT_SETUP.md
├── workflows/
│   ├── backend-ci.yml
│   ├── frontend-ci.yml
│   └── project-automation.yml
├── issues/
│   └── PHASE1_ISSUES.md
└── scripts/
    └── create-phase1-issues.sh
```

---

## 🎓 Learning Resources

- [GitHub Projects Docs](https://docs.github.com/en/issues/planning-and-tracking-with-projects)
- [GitHub CLI Manual](https://cli.github.com/manual/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Agile Methodology](https://www.atlassian.com/agile)

---

**Ready to start? Let's do this! 🚀**

```bash
# Step 1: Create repo
gh repo create filmpire-microservices --public --source=. --remote=origin
git push -u origin main

# Step 2: Create issues
./.github/scripts/create-phase1-issues.sh

# Step 3: Start first task
gh issue develop 2 --checkout

# Step 4: Tell me "let's implement task #2" and I'll help you!
```

