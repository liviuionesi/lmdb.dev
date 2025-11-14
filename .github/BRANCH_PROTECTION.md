# Branch Protection Configuration

## Current Status

⚠️ **Branch protection requires GitHub Pro or public repository**

The repository is currently private and on GitHub Free tier, which doesn't support branch protection rules. 

## Options

### Option 1: Make Repository Public
```bash
gh repo edit pehlivanu/filmpire-microservices --visibility public
```

Then run:
```bash
./.github/scripts/configure-branch-protection.sh
```

### Option 2: Upgrade to GitHub Pro
- Go to repository Settings → Options → Danger Zone
- Upgrade to GitHub Pro ($4/month for individuals)

### Option 3: Manual Configuration (When Available)

Once branch protection is available, configure the following for `main` branch:

#### Protection Rules for `main`:

1. **Require pull request before merging**
   - ✅ Require approvals: 1
   - ✅ Dismiss stale pull request approvals when new commits are pushed
   - ⬜ Require review from Code Owners (optional)

2. **Require status checks to pass**
   - ✅ Require branches to be up to date before merging
   - Required checks:
     - `build / Build & Test` (Backend CI)
     - `Web (Next.js)` (Frontend CI)
     - `Mobile (React Native)` (Frontend CI)

3. **Require conversation resolution before merging**
   - ✅ Enabled

4. **Do not allow bypassing the above settings**
   - ⬜ Do not allow force pushes
   - ⬜ Do not allow deletions

5. **Rules applied to administrators**
   - ⬜ Disabled (allow admins to bypass for emergencies)

#### Protection Rules for `develop` (if created):

Same as main, but:
- Required approving reviews: 1
- Enforce admins: No (more flexibility for development)

## Automated Script

The repository includes a script to automatically configure branch protection:

```bash
./.github/scripts/configure-branch-protection.sh
```

This script will:
- Configure protection for `main` branch
- Configure protection for `develop` branch (if it exists)
- Set up required status checks
- Enable required pull request reviews
- Disable force pushes and deletions

## Current Protections (Free Tier Workaround)

Since branch protection isn't available, follow these manual practices:

1. **Never commit directly to `main`**
   - Always create feature branches
   - Always use pull requests

2. **Always get code review**
   - Request review from at least 1 team member
   - Address all comments before merging

3. **Wait for CI to pass**
   - Ensure Backend CI (build & test) passes
   - Ensure Frontend CI passes
   - Check for any failing workflows

4. **Squash or rebase before merge**
   - Keep commit history clean
   - Use conventional commits

5. **Test locally before pushing**
   ```bash
   ./gradlew clean build
   cd frontend/web-nextjs && npm run build
   ```

## Verification

After branch protection is configured, verify at:
https://github.com/pehlivanu/filmpire-microservices/settings/branches

## References

- [GitHub Branch Protection Documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [GitHub Pricing](https://github.com/pricing)

