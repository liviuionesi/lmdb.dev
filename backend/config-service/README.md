# Config Service (Spring Cloud Config Server)

Spring Cloud Config Server for centralized configuration management in the Filmpire microservices architecture.

## Overview

The Config Service provides centralized configuration management for all microservices. It serves configuration files from a native filesystem (or Git repository in production) and supports environment-specific configurations.

**Port:** 8888

**Key Features:**
- Centralized configuration management
- Environment-specific configurations (dev, prod)
- Service-specific configurations
- Configuration versioning support
- Integration with Eureka for service discovery
- Hot reload of configurations (with Spring Cloud Bus)

## Running Locally

### Using Gradle

```bash
# From project root
./gradlew :backend:config-service:bootRun

# Or from service directory
cd backend/config-service
../../gradlew bootRun
```

### Using Docker

```bash
# Build image
docker build -t filmpire/config-service:latest .

# Run container
docker run -p 8888:8888 filmpire/config-service:latest
```

## Access Points

- **Configuration Endpoint:** http://localhost:8888/{application}/{profile}
- **Health Check:** http://localhost:8888/actuator/health
- **Service Info:** http://localhost:8888/actuator/info

## Configuration Structure

### Directory Layout

```
src/main/resources/config/
├── application.yml                 # Default configuration for all services
├── application-dev.yml             # Development environment overrides
├── application-prod.yml            # Production environment overrides
├── movie-service.yml              # Movie service specific config
├── user-service.yml               # User service specific config
├── actor-service.yml              # Actor service specific config
├── ai-service.yml                 # AI service specific config
├── media-service.yml              # Media service specific config
└── api-gateway.yml                # API Gateway specific config
```

### Configuration Priority

Configuration files are loaded in the following order (later overrides earlier):

1. `application.yml` - Common configuration for all services
2. `application-{profile}.yml` - Environment-specific overrides
3. `{service-name}.yml` - Service-specific configuration
4. `{service-name}-{profile}.yml` - Service and environment specific

### Native Mode (Development)

Currently configured to use native mode with configurations stored in `src/main/resources/config/`.

```yaml
spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config
```

### Git Mode (Production)

For production, switch to Git mode:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/filmpire-config-repo
          default-label: main
          clone-on-start: true
          username: ${GIT_USERNAME}
          password: ${GIT_PASSWORD}
```

## Configuration Files

### Common Configuration (application.yml)

- Logging configuration
- Management endpoints
- Common info

### Environment-Specific

**Development (application-dev.yml)**
- Debug logging
- Relaxed security
- Development database settings

**Production (application-prod.yml)**
- Optimized logging
- Strict security
- Production database settings

### Service-Specific Configurations

Each service has its own configuration file with:
- Database connection settings
- Service-specific properties
- Eureka registration settings
- Cache configurations
- API keys and secrets

## Testing

```bash
# Run all tests
./gradlew :backend:config-service:test

# Run specific test class
./gradlew :backend:config-service:test --tests ConfigServerIntegrationTest

# View coverage report
open backend/config-service/build/reports/tests/test/index.html
```

## API Examples

### Retrieve Default Configuration

```bash
curl http://localhost:8888/application/default
```

### Retrieve Service Configuration for Development

```bash
curl http://localhost:8888/movie-service/dev
```

### Retrieve Service Configuration for Production

```bash
curl http://localhost:8888/movie-service/prod
```

### JSON Format

```bash
curl http://localhost:8888/movie-service/dev | jq
```

## Client Configuration

Services connect to Config Service by adding these properties:

```yaml
spring:
  application:
    name: movie-service
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      fail-fast: true
```

## Encryption/Decryption

### Setup Encryption Key

```bash
export ENCRYPT_KEY=your-secret-key
```

### Encrypt Value

```bash
curl http://localhost:8888/encrypt -d "sensitive-value"
```

### Use Encrypted Value in Config

```yaml
database:
  password: '{cipher}AQA...'
```

## Health Monitoring

The Config Service monitors:
- Configuration repository connectivity
- Configuration file availability
- Eureka registration status

## Production Considerations

1. **Use Git Backend:** Store configurations in a private Git repository
2. **Enable Encryption:** Encrypt sensitive values (passwords, API keys)
3. **Setup Spring Cloud Bus:** Enable dynamic configuration refresh
4. **Configure Security:** Add authentication for config endpoints
5. **Enable Audit:** Track configuration changes
6. **Use Profiles:** Separate dev, staging, and prod configurations

## Troubleshooting

**Config Service Not Starting:**
- Check if port 8888 is available
- Verify configuration repository is accessible
- Check application logs for errors

**Services Can't Connect:**
- Verify Config Service is running on port 8888
- Check Eureka registration
- Verify client configuration includes correct Config Service URL

**Configuration Not Loading:**
- Check file names match service names
- Verify profile names are correct
- Check config server logs for file loading

## Related Documentation

- [Spring Cloud Config](https://spring.io/projects/spring-cloud-config)
- [Architecture Documentation](../../docs/architecture/ARCHITECTURE.md)
- [Configuration Best Practices](../../docs/architecture/CONFIGURATION.md)

## Development Workflow

1. **Add New Configuration:**
   - Create `{service-name}.yml` in `src/main/resources/config/`
   - Add environment-specific overrides in `{service-name}-{profile}.yml`
   - Test with `curl http://localhost:8888/{service-name}/{profile}`

2. **Update Existing Configuration:**
   - Modify the YAML file
   - Restart Config Service (or use Spring Cloud Bus for hot reload)
   - Verify changes with curl

3. **Add New Environment:**
   - Create `application-{env}.yml`
   - Add environment-specific properties
   - Test with services using new profile

## Security Notes

- Never commit sensitive data (passwords, API keys) unencrypted
- Use encryption for all sensitive values
- Restrict access to Config Service in production
- Use private Git repository for configurations
- Rotate encryption keys periodically

## Performance

- Configuration files are cached after first load
- Git backend clones repository on startup
- Consider using Spring Cloud Bus for configuration refresh without restart
- Use native mode for faster local development

## Version History

- **v1.0.0** - Initial implementation with native mode support
- Environment-specific and service-specific configurations
- Integration with Eureka service discovery
