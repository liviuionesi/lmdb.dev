# Discovery Service (Eureka Server)

Spring Cloud Netflix Eureka Server for service discovery and registration in the Filmpire microservices architecture.

## Overview

The Discovery Service acts as the central registry where all microservices register themselves and discover other services. It enables client-side load balancing and failover.

**Port:** 8761

**Key Features:**
- Service registration and discovery
- Health monitoring and status tracking
- Self-preservation mode (disabled for dev)
- REST API for service queries
- Web-based Eureka dashboard

## Running Locally

### Using Gradle

```bash
# From project root
./gradlew :backend:discovery-service:bootRun

# Or from service directory
cd backend/discovery-service
../../gradlew bootRun
```

### Using Docker

```bash
# Build image
docker build -t filmpire/discovery-service:latest .

# Run container
docker run -p 8761:8761 filmpire/discovery-service:latest
```

## Access Points

- **Eureka Dashboard:** http://localhost:8761
- **Health Check:** http://localhost:8761/actuator/health
- **Service Info:** http://localhost:8761/actuator/info
- **Registered Apps:** http://localhost:8761/eureka/apps

## Configuration

### Standalone Mode (Development)

```yaml
eureka:
  client:
    register-with-eureka: false  # Don't register itself
    fetch-registry: false        # Don't fetch registry
  server:
    enable-self-preservation: false  # Disable for faster de-registration in dev
```

### Client Registration

Services register with Eureka using:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

## Testing

```bash
# Run all tests
./gradlew :backend:discovery-service:test

# Run with coverage
./gradlew :backend:discovery-service:test jacocoTestReport

# View coverage report
open backend/discovery-service/build/reports/jacoco/test/html/index.html
```

## Service Registration Example

When services start, they register with Eureka:

```bash
# Check registered instances
curl http://localhost:8761/eureka/apps

# Check specific service
curl http://localhost:8761/eureka/apps/MOVIE-SERVICE
```

## Health Monitoring

The Discovery Service monitors:
- Number of registered instances
- Heartbeat status
- Renewal threshold
- Self-preservation mode status

## Production Considerations

For production deployment:

1. **Enable self-preservation:**
   ```yaml
   eureka.server.enable-self-preservation: true
   ```

2. **Use multiple Eureka instances** (peer-to-peer replication)

3. **Configure proper timeouts:**
   ```yaml
   eureka.server.eviction-interval-timer-in-ms: 60000
   eureka.instance.lease-renewal-interval-in-seconds: 30
   ```

4. **Enable security** with Spring Security

## Troubleshooting

**Services not showing up:**
- Check network connectivity
- Verify `eureka.client.service-url.defaultZone` is correct
- Check service logs for registration errors

**Self-preservation mode activated:**
- Expected in dev when stopping/starting services frequently
- Disable in dev: `eureka.server.enable-self-preservation: false`

**Slow de-registration:**
- Adjust `eviction-interval-timer-in-ms` (default: 60s)
- In dev, set to lower value (e.g., 15s)

## Related Documentation

- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)
- [Eureka Wiki](https://github.com/Netflix/eureka/wiki)
- [Architecture Documentation](../../docs/architecture/ARCHITECTURE.md)

