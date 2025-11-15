#!/bin/bash

# Create Phase 3 Issues: Core Microservices
# Based on PHASE3_CORE_SERVICES.md

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Creating Phase 3 Issues - Core Microservices           ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed${NC}"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo -e "${RED}Error: Not authenticated. Run: gh auth login${NC}"
    exit 1
fi

# Check if issues already exist
echo -e "${BLUE}Checking for existing issues...${NC}"
EXISTING=$(gh issue list --search "Core Microservices" --json number --jq 'length' 2>/dev/null || echo "0")

if [ "$EXISTING" != "0" ]; then
    echo -e "${YELLOW}⚠ Some Phase 3 issues may already exist. Continue anyway? (y/n)${NC}"
    read -r response
    if [ "$response" != "y" ]; then
        exit 0
    fi
fi

echo ""
echo -e "${BLUE}To create Phase 3 issues, use one of these methods:${NC}"
echo ""
echo -e "${GREEN}Method 1: Manual via GitHub Web UI${NC}"
echo "1. Go to: https://github.com/YOUR_USERNAME/filmpire-microservices/issues/new"
echo "2. Copy content from: .github/issues/PHASE3_CORE_SERVICES.md"
echo "3. Fill in title, labels, and body"
echo ""
echo -e "${GREEN}Method 2: Use GitHub CLI (recommended)${NC}"
echo ""
echo "Create Epic #11:"
echo 'gh issue create \'
echo '  --title "[EPIC] Core Microservices" \'
echo '  --label "epic,P0-critical,sprint-3,backend" \'
echo '  --body "$(cat <<'\''EOF'\''
# Epic Description
Implement core business logic microservices: Movie Service, User Service, and Actor Service.

# Business Value
Delivers core functionality for movie discovery, user management, and actor information.

# User Stories
- #12 - Implement Movie Service
- #13 - Implement User Service  
- #14 - Implement Actor Service
- #15 - Service Integration Testing

# Story Points: 34
# Target Sprint: Sprint 3-5

See .github/issues/PHASE3_CORE_SERVICES.md for full details.
EOF
)"'
echo ""
echo "Then create tasks #12-15 similarly."
echo ""
echo -e "${YELLOW}Would you like me to create them now? (y/n)${NC}"
read -r create_now

if [ "$create_now" = "y" ]; then
    echo ""
    echo -e "${BLUE}Creating Epic #11...${NC}"
    
    EPIC11=$(gh issue create \
      --title "[EPIC] Core Microservices" \
      --label "epic,P0-critical,sprint-3,backend" \
      --body "$(cat <<'EOF'
## Epic Description
Implement core business logic microservices: Movie Service, User Service, and Actor Service.

## Business Value
Delivers core functionality for movie discovery, user management, and actor information.

## User Stories
- #12 - Implement Movie Service
- #13 - Implement User Service  
- #14 - Implement Actor Service
- #15 - Service Integration Testing

## Story Points
34

## Target Sprint
Sprint 3-5

## Estimated Time
25-30 hours

## Dependencies
Phase 2 (Infrastructure Services) ✅

## Acceptance Criteria
- [ ] Movie Service fully functional
- [ ] User Service fully functional
- [ ] Actor Service fully functional
- [ ] All services integrated and tested
- [ ] API documentation complete
- [ ] All tests passing (>85% coverage)

## Definition of Done
- [ ] All code reviewed and merged
- [ ] Tests passing (85%+ coverage)
- [ ] Documentation updated
- [ ] Services deployed to dev
- [ ] Health checks passing

For detailed implementation, see: .github/issues/PHASE3_CORE_SERVICES.md
EOF
)")
    
    echo -e "${GREEN}✓ Epic #11 created: $EPIC11${NC}"
    echo ""
    echo -e "${YELLOW}Note: Tasks #12-15 should be created manually by copying content from PHASE3_CORE_SERVICES.md${NC}"
    echo -e "${BLUE}Or use: gh issue create --title \"...\" --label \"...\" --body-file .github/issues/PHASE3_CORE_SERVICES.md${NC}"
fi

echo ""
echo -e "${GREEN}Done!${NC}"

