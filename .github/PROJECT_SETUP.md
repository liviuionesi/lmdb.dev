# GitHub Projects Setup Guide

## Step 1: Create GitHub Repository

```bash
# Initialize git (if not done)
cd /home/liviu/Desktop/filmpire-microservices
git init
git add .
git commit -m "Initial commit: Project structure and documentation"

# Create repo on GitHub (via CLI)
gh repo create filmpire-microservices --public --source=. --remote=origin
git push -u origin main
```

## Step 2: Create GitHub Project Board

### Via GitHub CLI:
```bash
# Create project
gh project create --owner @me --title "Filmpire Microservices" --format board

# Or via web: https://github.com/users/YOUR_USERNAME/projects/new
```

### Via Web Interface:
1. Go to: https://github.com/YOUR_USERNAME?tab=projects
2. Click "New project"
3. Choose "Board" template
4. Name: "Filmpire Microservices - Sprint Board"

## Step 3: Configure Project Board

### Columns to Create:
1. **📋 Backlog** - All planned work
2. **🎯 Sprint Ready** - Ready to be worked on
3. **🚧 In Progress** - Currently being implemented
4. **👀 In Review** - PR created, awaiting review
5. **✅ Done** - Completed and merged

### Custom Fields to Add:
- **Priority**: P0 (Critical), P1 (High), P2 (Medium), P3 (Low)
- **Type**: Epic, User Story, Task, Bug
- **Service**: api-gateway, movie-service, user-service, etc.
- **Sprint**: Sprint 0, Sprint 1, Sprint 2...
- **Story Points**: 1, 2, 3, 5, 8, 13

## Step 4: Set Up Automation

### Automated Workflows:
1. **Issue Created** → Move to "Backlog"
2. **Issue Assigned** → Move to "Sprint Ready"
3. **PR Opened** → Move to "In Review"
4. **PR Merged** → Move to "Done" + Close Issue
5. **PR Closed (not merged)** → Move back to "Sprint Ready"

### GitHub Actions Integration:
- Auto-link PRs to issues via branch naming
- Auto-update project board on PR status changes
- Auto-label based on file paths changed

## Step 5: Import Initial Issues

```bash
# This will create all Phase 1 issues
gh issue create --title "Epic: Project Setup Phase" --body-file .github/issues/epic-project-setup.md --label "epic,P0,sprint-0"

# Individual tasks
gh issue create --title "Task: Initialize Project Structure" --body-file .github/issues/task-01-structure.md --label "task,P0,sprint-0"
gh issue create --title "Task: Setup Gradle Multi-Module Build" --body-file .github/issues/task-02-gradle.md --label "task,P0,sprint-0"
gh issue create --title "Task: Create GitHub Templates & Workflows" --body-file .github/issues/task-03-github.md --label "task,P0,sprint-0"
```

## Step 6: Development Workflow

### Creating a New Task:
```bash
# Create branch from issue
gh issue develop 123 --checkout

# This creates branch: 123-task-description
```

### Working on Task:
```bash
# Make changes
git add .
git commit -m "feat(setup): implement gradle configuration

- Add multi-module build setup
- Configure Spring Boot 3.5.8
- Add dependency management

Closes #123"

git push origin 123-task-description
```

### Creating Pull Request:
```bash
# Create PR linked to issue
gh pr create --title "feat(setup): implement gradle configuration" --body "Closes #123" --assignee @me

# Or let me create it for you automatically
```

### Review & Merge:
```bash
# After CI passes and review approved
gh pr merge 123 --squash --delete-branch
```

## Step 7: Sprint Planning

### Start of Sprint:
1. Move issues from "Backlog" to "Sprint Ready"
2. Assign issues to team members
3. Set sprint milestone
4. Start working!

### Daily Standup:
Check project board:
```bash
gh project list
gh project item-list <PROJECT_NUMBER> --owner @me
```

### End of Sprint:
1. Review completed work
2. Move incomplete items back to backlog
3. Update documentation
4. Create release

## Recommended Labels

```bash
# Create labels
gh label create "P0-critical" --color "d73a4a" --description "Critical priority"
gh label create "P1-high" --color "ff6b6b" --description "High priority"
gh label create "P2-medium" --color "ffd93d" --description "Medium priority"
gh label create "P3-low" --color "6bcf7f" --description "Low priority"

gh label create "epic" --color "5319e7" --description "Epic"
gh label create "user-story" --color "0075ca" --description "User story"
gh label create "task" --color "008672" --description "Task"
gh label create "bug" --color "d73a4a" --description "Bug"

gh label create "backend" --color "fbca04" --description "Backend service"
gh label create "frontend" --color "006b75" --description "Frontend"
gh label create "infrastructure" --color "0e8a16" --description "Infrastructure"
gh label create "docs" --color "c5def5" --description "Documentation"
```

## Integration with Cursor

When working in Cursor:
1. Check current issue: `gh issue view <NUMBER>`
2. Make changes
3. I'll help you test, commit, and create PR
4. Auto-updates project board

## Next Steps

After setup:
1. Create all Phase 1 issues (see `.github/issues/` directory)
2. Move them to project board
3. Start with Task #1: Project Structure
4. Follow TDD approach for each task
5. Create PR after each task completion

