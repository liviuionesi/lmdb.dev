# Security Best Practices for Config Service

## Overview

The Config Service manages centralized configuration for all microservices. Proper handling of sensitive data is critical.

## Environment Variables Strategy

### What NOT to Commit
❌ Database passwords
❌ API keys
❌ JWT secrets
❌ Encryption keys
❌ Service account credentials
❌ OAuth client secrets

### What IS Safe to Commit
✅ Configuration structure (YAML files)
✅ Non-sensitive defaults (ports, timeouts, cache TTLs)
✅ Environment variable placeholders (`${VAR_NAME}`)
✅ Service endpoints (localhost references)

## Configuration Approach

All sensitive values use Spring Boot's property placeholder syntax:

```yaml
spring:
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/filmpire}
    username: ${POSTGRES_USERNAME:postgres}
    password: ${POSTGRES_PASSWORD}  # No default - MUST be set
```

**Syntax:** `${ENV_VAR:default_value}`
- Use defaults for non-sensitive values (URLs, ports)
- Omit defaults for sensitive values (forces explicit configuration)

## Local Development Setup

### 1. Copy Environment Template

```bash
cp backend/config-service/.env.example backend/config-service/.env
```

### 2. Fill in Your Values

Edit `.env` with your actual credentials:

```bash
POSTGRES_PASSWORD=my-secure-password
JWT_SECRET=$(openssl rand -base64 64)
MONGODB_URI=mongodb://admin:mypassword@localhost:27017/filmpire?authSource=admin
```

### 3. Load Environment Variables

**Option A: Using direnv (recommended)**
```bash
# Install direnv
sudo apt install direnv  # or brew install direnv

# Create .envrc
echo 'dotenv' > .envrc
direnv allow
```

**Option B: Manual export**
```bash
export $(cat backend/config-service/.env | xargs)
```

**Option C: Docker Compose**
```yaml
services:
  config-service:
    env_file:
      - backend/config-service/.env
```

## Production Deployment

### Option 1: Spring Cloud Config Encryption

```bash
# Set encryption key
export ENCRYPT_KEY=my-super-secret-symmetric-key

# Encrypt sensitive values
curl http://config-server:8888/encrypt -d "my-password"
# Returns: {cipher}AQA...encrypted-value...
```

Use encrypted values in config files:

```yaml
spring:
  datasource:
    password: '{cipher}AQA...encrypted-value...'
```

### Option 2: External Secret Management

**Vault Integration:**
```yaml
spring:
  cloud:
    config:
      server:
        vault:
          host: vault.example.com
          port: 8200
          token: ${VAULT_TOKEN}
```

**AWS Secrets Manager:**
```yaml
spring:
  cloud:
    config:
      server:
        aws-secretsmanager:
          enabled: true
          region: us-east-1
```

**Kubernetes Secrets:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: filmpire-secrets
type: Opaque
data:
  postgres-password: base64-encoded-value
  jwt-secret: base64-encoded-value
```

Mount as environment variables in pod:
```yaml
env:
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: filmpire-secrets
        key: postgres-password
```

### Option 3: Environment Variables in CI/CD

**GitHub Actions:**
```yaml
env:
  POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
```

**GitLab CI:**
```yaml
variables:
  POSTGRES_PASSWORD: $DB_PASSWORD
  JWT_SECRET: $JWT_SECRET_KEY
```

## Security Checklist

- [ ] `.env` added to `.gitignore`
- [ ] `.env.example` created with safe defaults
- [ ] All sensitive values use `${ENV_VAR}` syntax
- [ ] No hardcoded passwords in YAML files
- [ ] JWT secret is minimum 512 bits (64+ characters)
- [ ] Database passwords are strong (16+ characters)
- [ ] Production uses encrypted values or external secret management
- [ ] Secrets rotation policy defined
- [ ] Access to Config Server is restricted (authentication required)
- [ ] Audit logging enabled for config changes

## Generating Secure Secrets

### JWT Secret (minimum 512 bits)
```bash
openssl rand -base64 64
```

### Database Password
```bash
openssl rand -base64 32
```

### Encryption Key for Config Server
```bash
openssl rand -hex 32
```

## Git Security

### Check for Accidentally Committed Secrets

```bash
# Scan repository history
git log -p | grep -i 'password\|secret\|key' | head -50

# Use git-secrets tool
git secrets --scan
```

### Remove Committed Secrets (if found)

```bash
# Use BFG Repo-Cleaner
bfg --replace-text passwords.txt

# Or git filter-branch
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch path/to/sensitive/file" \
  --prune-empty --tag-name-filter cat -- --all
```

⚠️ **If secrets were committed, they are compromised. Rotate immediately!**

## Access Control

### Development
- Use local `.env` files (not committed)
- Each developer has their own credentials
- Shared dev database with non-sensitive data

### Staging/Production
- Environment variables injected by deployment platform
- Secrets stored in secret management system
- Principle of least privilege (services only get needed secrets)
- Regular secret rotation (90 days recommended)

## Monitoring & Auditing

Enable Config Server audit logging:

```yaml
spring:
  cloud:
    config:
      server:
        monitor:
          enabled: true
```

Monitor access to sensitive endpoints:
- `/encrypt` - encryption requests
- `/{application}/{profile}` - config retrieval
- Failed authentication attempts

## Emergency Response

If secrets are compromised:

1. **Immediate:** Revoke/rotate compromised credentials
2. **Verify:** Check access logs for unauthorized use
3. **Update:** Deploy new secrets to all environments
4. **Investigate:** Determine how compromise occurred
5. **Document:** Record incident for future prevention

## References

- [Spring Cloud Config Encryption](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_encryption_and_decryption)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [12-Factor App: Config](https://12factor.net/config)

