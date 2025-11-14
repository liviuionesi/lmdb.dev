#!/bin/bash
# Script to configure branch protection rules for main and develop branches
# Requires GitHub CLI (gh) to be installed and authenticated

set -e

REPO="pehlivanu/filmpire-microservices"

echo "🔒 Configuring branch protection rules for $REPO..."

# Function to configure branch protection
configure_branch() {
    local branch=$1
    echo "⚙️  Configuring protection for branch: $branch"
    
    gh api repos/$REPO/branches/$branch/protection \
        --method PUT \
        --input - <<EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "build / Build & Test",
      "Web (Next.js)",
      "Mobile (React Native)"
    ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
EOF

    if [ $? -eq 0 ]; then
        echo "✅ Branch protection configured for $branch"
    else
        echo "❌ Failed to configure branch protection for $branch"
        return 1
    fi
}

# Configure main branch
configure_branch "main"

# Configure develop branch (if it exists)
if gh api repos/$REPO/branches/develop &>/dev/null; then
    configure_branch "develop"
else
    echo "⚠️  Develop branch doesn't exist yet, skipping..."
fi

echo ""
echo "✅ Branch protection configuration complete!"
echo ""
echo "📋 Protection Summary:"
echo "  - Required status checks: Backend CI, Frontend CI"
echo "  - Required approving reviews: 1"
echo "  - Dismiss stale reviews: enabled"
echo "  - Require conversation resolution: enabled"
echo "  - Force push: disabled"
echo "  - Branch deletion: disabled"
echo ""
echo "🔗 View settings: https://github.com/$REPO/settings/branches"

