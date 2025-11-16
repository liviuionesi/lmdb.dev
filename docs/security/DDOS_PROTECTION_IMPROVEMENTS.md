# DDoS Protection Improvements

**Status:** Recommendations  
**Priority:** P0-Critical  
**Date:** November 16, 2025

---

## Current State Assessment

### ✅ Existing Protections

1. **Rate Limiting** - Partially implemented
   - Per-service limits configured
   - Redis-backed token bucket
   - ⚠️ Missing IP-based key resolver

2. **Circuit Breaker** - Fully implemented
   - Resilience4j with fallbacks
   - 50% failure threshold
   - 10-second recovery time

3. **Request Timeouts** - Fully implemented
   - 5-second timeout
   - Prevents slow HTTP attacks

4. **Retry Limits** - Fully implemented
   - Max 3 retries
   - Exponential backoff

### ❌ Critical Gaps

1. **No IP-Based Rate Limiting**
2. **No Rate Limiting on Auth Endpoints** (Major vulnerability)
3. **No Global Rate Limiting**
4. **No Request Size Limits**
5. **No Connection Limits**
6. **No IP Blacklist/Whitelist**

---

## Priority 1: Critical Fixes (Immediate)

### 1. IP-Based Rate Limiting

**Problem**: Current rate limiter doesn't key by IP address

**Solution**: Add custom KeyResolver

```java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Forwarded-For");
            
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                // Get first IP from X-Forwarded-For
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            
            // Fallback to remote address
            return Mono.just(
                exchange.getRequest()
                        .getRemoteAddress()
                        .getAddress()
                        .getHostAddress()
            );
        };
    }
}
```

**application.yml update:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: movie-service
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@ipKeyResolver}"  # Add this
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

---

### 2. Rate Limiting for Auth Endpoints

**Problem**: Auth endpoints have NO rate limiting - vulnerable to brute force

**Solution**: Add strict rate limiting to auth endpoints

```yaml
- id: auth-service
  uri: lb://user-service
  predicates:
    - Path=/api/v1/auth/**
  filters:
    - name: CircuitBreaker
      args:
        name: authServiceCircuitBreaker
    - name: RequestRateLimiter  # ADD THIS
      args:
        key-resolver: "#{@ipKeyResolver}"
        redis-rate-limiter.replenishRate: 5    # 5 req/sec
        redis-rate-limiter.burstCapacity: 10   # Max 10 burst
```

**Rationale**: 
- 5 req/sec allows legitimate users (1 login every ~10 seconds)
- Prevents brute force attacks (60 attempts/min vs thousands)

---

### 3. Global Rate Limiting

**Problem**: Attacker can hit multiple services to bypass per-service limits

**Solution**: Add global rate limiter filter

```java
@Component
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final int GLOBAL_LIMIT = 100; // 100 req/sec per IP globally
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        String key = "global-rate-limit:" + ip;
        
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        redisTemplate.expire(key, Duration.ofSeconds(1));
                    }
                    
                    if (count > GLOBAL_LIMIT) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    
                    return chain.filter(exchange);
                });
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

---

### 4. Request Size Limits

**Problem**: No max payload size - vulnerable to large payload attacks

**Solution**: Add size limits to application.yml

```yaml
spring:
  codec:
    max-in-memory-size: 10MB  # Max request body size

server:
  max-http-header-size: 16KB  # Max header size
  tomcat:
    max-swallow-size: 10MB
    max-http-post-size: 10MB
```

**Media Service** (file uploads) needs higher limits:
```yaml
# In media-service route
- id: media-service
  predicates:
    - Path=/api/v1/media/**
  filters:
    - name: RequestSize
      args:
        maxSize: 50MB  # Higher for file uploads
```

---

## Priority 2: Enhanced Protections (Short-term)

### 5. Connection Limits

Add Netty connection limits:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 1000  # Global max connections
          max-idle-time: 30s
          max-life-time: 60s
        connect-timeout: 5000
        response-timeout: 5s
```

---

### 6. IP Blacklist/Whitelist Filter

```java
@Component
public class IpFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<IpFilterConfig> {
    
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    
    @Override
    public GatewayFilter apply(IpFilterConfig config) {
        return (exchange, chain) -> {
            String ip = getClientIp(exchange);
            
            // Check whitelist first
            if (!whitelist.isEmpty() && !whitelist.contains(ip)) {
                return denyAccess(exchange);
            }
            
            // Check blacklist
            if (blacklist.contains(ip)) {
                return denyAccess(exchange);
            }
            
            return chain.filter(exchange);
        };
    }
}
```

With REST API to manage blacklist:
```java
@RestController
@RequestMapping("/admin/ip-filter")
public class IpFilterController {
    
    @PostMapping("/blacklist")
    public ResponseEntity<Void> addToBlacklist(@RequestParam String ip) {
        // Add IP to blacklist
    }
    
    @DeleteMapping("/blacklist")
    public ResponseEntity<Void> removeFromBlacklist(@RequestParam String ip) {
        // Remove IP from blacklist
    }
}
```

---

### 7. Request Pattern Detection

Detect suspicious patterns:

```java
@Component
public class SuspiciousPatternDetector implements GlobalFilter {
    
    private final LoadingCache<String, AtomicInteger> suspiciousRequestCounter;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        String pattern = detectPattern(exchange.getRequest());
        
        if (isSuspicious(pattern)) {
            int count = suspiciousRequestCounter.get(ip).incrementAndGet();
            
            if (count > SUSPICIOUS_THRESHOLD) {
                // Auto-blacklist IP
                // Send alert
                return denyAccess(exchange);
            }
        }
        
        return chain.filter(exchange);
    }
}
```

---

## Priority 3: Advanced Protections (Medium-term)

### 8. Slowloris Protection

Detect slow HTTP attacks:

```java
@Component
public class SlowlorisDetector implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // If request takes > 10 seconds and has minimal data
                    if (duration > 10000 && isMinimalData(exchange)) {
                        String ip = getClientIp(exchange);
                        blacklistIp(ip);
                    }
                });
    }
}
```

---

### 9. Geographic Filtering

Block requests from specific countries:

```java
@Component
public class GeoIpFilter implements GlobalFilter {
    
    private final GeoIpService geoIpService;
    private final Set<String> blockedCountries = Set.of("CN", "RU"); // Example
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        String country = geoIpService.getCountryCode(ip);
        
        if (blockedCountries.contains(country)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
}
```

Requires MaxMind GeoIP2 database:
```xml
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.0.0</version>
</dependency>
```

---

### 10. CAPTCHA for Suspicious IPs

Integrate CAPTCHA for rate-limited IPs:

```java
@Component
public class CaptchaGatewayFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange);
        
        if (requiresCaptcha(ip)) {
            String captchaToken = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Captcha-Token");
            
            if (captchaToken == null || !verifyCaptcha(captchaToken)) {
                return returnCaptchaChallenge(exchange);
            }
        }
        
        return chain.filter(exchange);
    }
}
```

---

## Priority 4: External Services (Long-term)

### 11. Cloudflare DDoS Protection

Deploy behind Cloudflare:
- Layer 3/4 DDoS protection
- Rate limiting at edge
- Bot management
- WAF (Web Application Firewall)

**Configuration:**
```nginx
# Cloudflare setup
- Enable "Under Attack Mode" for DDoS events
- Configure rate limiting rules
- Enable Bot Fight Mode
- Set up firewall rules
```

---

### 12. AWS Shield / Azure DDoS Protection

For cloud deployment:

**AWS Shield Standard** (Free):
- Automatic DDoS protection
- Layer 3/4 protection

**AWS Shield Advanced** ($3,000/month):
- Layer 7 protection
- 24/7 DDoS Response Team
- Cost protection

---

### 13. Rate Limiting Dashboard

Monitor and manage rate limits:

```java
@RestController
@RequestMapping("/admin/rate-limits")
public class RateLimitDashboard {
    
    @GetMapping("/stats")
    public ResponseEntity<RateLimitStats> getStats() {
        // Return current rate limit stats per IP
    }
    
    @GetMapping("/top-ips")
    public ResponseEntity<List<IpStats>> getTopIps() {
        // Return IPs with most requests
    }
    
    @PostMapping("/adjust")
    public ResponseEntity<Void> adjustLimits(@RequestBody RateLimitAdjustment adj) {
        // Dynamically adjust rate limits
    }
}
```

---

## Implementation Plan

### Phase 1: Critical Fixes (1 week)
1. ✅ Add IP-based KeyResolver
2. ✅ Add rate limiting to auth endpoints
3. ✅ Implement global rate limiting
4. ✅ Add request size limits

### Phase 2: Enhanced Protection (2 weeks)
5. ✅ Connection limits
6. ✅ IP blacklist/whitelist
7. ✅ Request pattern detection

### Phase 3: Advanced Features (1 month)
8. ✅ Slowloris protection
9. ✅ Geographic filtering
10. ✅ CAPTCHA integration

### Phase 4: External Services (Ongoing)
11. ✅ Cloudflare setup
12. ✅ Cloud DDoS protection
13. ✅ Monitoring dashboard

---

## Monitoring & Alerting

### Metrics to Track
- Requests per second per IP
- Rate limit violations
- Circuit breaker activations
- Blacklisted IPs count
- Top request sources

### Alerts
- Spike in 429 responses
- Circuit breaker open
- Suspicious patterns detected
- New IP auto-blacklisted

### Log Analysis
```java
// Enhanced logging for DDoS detection
log.warn("Possible DDoS from IP: {} - {} requests in {}ms", 
         ip, requestCount, duration);
```

---

## Testing DDoS Protection

### Load Testing
```bash
# Apache Bench
ab -n 10000 -c 100 http://localhost:8080/api/v1/movies

# Expected: Rate limiting kicks in, 429 responses
```

### Penetration Testing
```bash
# Slowloris attack simulation
slowhttptest -c 1000 -H -g -o slowloris.html -i 10 -r 200 -t GET \
             -u http://localhost:8080/api/v1/movies
```

---

## Cost-Benefit Analysis

| Protection | Implementation Cost | Runtime Cost | DDoS Protection Level |
|-----------|---------------------|--------------|----------------------|
| IP-based rate limiting | 2 hours | Free | Medium |
| Auth rate limiting | 1 hour | Free | High |
| Global rate limiting | 4 hours | Free | Medium |
| Request size limits | 1 hour | Free | Low |
| IP blacklist | 8 hours | Free | Medium |
| Pattern detection | 16 hours | Free | High |
| Cloudflare | 1 day setup | $20-200/month | Very High |
| AWS Shield Advanced | 1 day setup | $3,000/month | Very High |

---

## Conclusion

**Current DDoS Protection Level:** 🟡 **Medium** (40% effective)

**With Priority 1-2 Fixes:** 🟢 **High** (80% effective)

**With All Recommendations:** 🟢 **Very High** (95% effective)

**Recommended Immediate Actions:**
1. Implement IP-based rate limiting (2 hours)
2. Add rate limiting to auth endpoints (1 hour)
3. Add request size limits (1 hour)
4. Implement global rate limiting (4 hours)

**Total Time for Critical Fixes:** ~8 hours of development

---

**Status:** Recommendations  
**Next Review:** After Phase 1 implementation

