# Filmpire Shared Library

Common library containing DTOs, exceptions, utilities, constants, and annotations used across all Filmpire microservices.

## 📋 Contents

- **DTOs** - Common data transfer objects for API responses
- **Exceptions** - Custom exception classes for error handling
- **Utilities** - Helper classes for common operations
- **Constants** - Shared constants and error codes
- **Annotations** - Custom annotations for cross-cutting concerns

## 🚀 Quick Start

### Adding Dependency

Add this library as a dependency in your service's `build.gradle`:

```groovy
dependencies {
    implementation project(':backend:shared-library')
}
```

### Using as Java Module (JPMS)

The shared-library can also be used as a Java module. In your service's `module-info.java`:

```java
module com.filmpire.movie {
    requires com.filmpire.shared;
    // ... other requires
}
```

**Note:** The library works both as a Java module and as a regular classpath dependency (backward compatible). You can use either approach depending on your project's needs.

### Usage Example

```java
import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.util.ValidationUtils;

@RestController
public class MovieController {
    
    @GetMapping("/api/v1/movies/{id}")
    public ApiResponse<Movie> getMovie(@PathVariable String id) {
        ValidationUtils.notBlank(id, "movieId");
        
        Movie movie = movieService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", id));
        
        return ApiResponse.success(movie);
    }
}
```

## 📦 Components

### DTOs

#### ApiResponse<T>

Generic wrapper for all API responses with success/error handling:

```java
// Success response
ApiResponse<Movie> response = ApiResponse.success(movie);

// Success with custom message and status
ApiResponse<Movie> response = ApiResponse.success(movie, "Movie found", 200);

// Error response
ApiResponse<Movie> response = ApiResponse.error("Movie not found", 404);

// Error with path
ApiResponse<Movie> response = ApiResponse.error("Not found", 404, "/api/movies/123");
```

**Fields:**
- `boolean success` - Indicates success/failure
- `String message` - Human-readable message
- `T data` - Response data (null on error)
- `LocalDateTime timestamp` - Response timestamp
- `int statusCode` - HTTP status code
- `String path` - Request path (optional)

#### ErrorResponse

Detailed error response for exception handling:

```java
ErrorResponse error = ErrorResponse.of(
    404, 
    "ERR_1002", 
    "Resource not found", 
    "/api/movies/123"
);

// With validation errors
Map<String, String> fieldErrors = Map.of(
    "email", "Invalid email format",
    "password", "Password too weak"
);
ErrorResponse error = ErrorResponse.withValidationErrors(
    400, 
    "ERR_1001", 
    "Validation failed",
    "/api/users",
    fieldErrors
);
```

**Fields:**
- `int status` - HTTP status code
- `String errorCode` - Error code for client handling
- `String message` - Error message
- `String details` - Detailed description (optional)
- `String path` - Request path
- `LocalDateTime timestamp` - Error timestamp
- `Map<String, String> fieldErrors` - Field-level validation errors
- `List<String> stackTrace` - Stack trace (dev mode only)
- `Map<String, Object> metadata` - Additional metadata

#### PageResponse<T>

Paginated response wrapper:

```java
List<Movie> movies = movieService.findAll(page, size);
PageResponse<Movie> response = PageResponse.of(movies, 0, 20, 150);

// Empty page
PageResponse<Movie> empty = PageResponse.empty(0, 20);
```

**Fields:**
- `List<T> content` - Page items
- `int pageNumber` - Current page (zero-indexed)
- `int pageSize` - Items per page
- `long totalElements` - Total items across all pages
- `int totalPages` - Total number of pages
- `boolean first` - Is first page
- `boolean last` - Is last page
- `boolean hasNext` - Has next page
- `boolean hasPrevious` - Has previous page
- `int numberOfElements` - Items in current page

### Exceptions

All custom exceptions extend `RuntimeException` and result in specific HTTP status codes:

#### ResourceNotFoundException (404)

```java
// Basic usage
throw new ResourceNotFoundException("Movie with id '123' not found");

// With resource type and ID
throw new ResourceNotFoundException("Movie", "123");

// With field lookup
throw new ResourceNotFoundException("User", "email", "john@example.com");
```

#### ValidationException (400)

```java
// Single field error
throw new ValidationException("email", "Invalid email format");

// Multiple field errors
Map<String, String> errors = new HashMap<>();
errors.put("email", "Invalid email");
errors.put("password", "Too weak");
throw new ValidationException("Validation failed", errors);
```

#### BusinessException (422)

```java
throw new BusinessException("Cannot delete movie with active bookings");
throw new BusinessException("Insufficient credits", "ERR_INSUFFICIENT_CREDITS");
```

#### UnauthorizedException (401)

```java
throw new UnauthorizedException("Invalid credentials");
throw new UnauthorizedException("Token expired", cause);
```

#### ForbiddenException (403)

```java
throw new ForbiddenException("You don't have permission to access this resource");
```

#### ServiceUnavailableException (503)

```java
throw new ServiceUnavailableException("TMDB API", "Service temporarily unavailable");
```

### Utilities

#### StringUtils

Common string operations:

```java
// Null/Empty checks
StringUtils.isEmpty(str);
StringUtils.isBlank(str);
StringUtils.isNotEmpty(str);
StringUtils.isNotBlank(str);

// Defaults
String result = StringUtils.defaultIfEmpty(str, "default");
String result = StringUtils.defaultIfBlank(str, "default");

// Transformations
String result = StringUtils.capitalize("hello");        // "Hello"
String result = StringUtils.toCamelCase("hello_world"); // "helloWorld"
String result = StringUtils.toSnakeCase("helloWorld");  // "hello_world"

// Truncation
String result = StringUtils.truncate("long text", 10);
String result = StringUtils.truncateWithEllipsis("long text", 10);

// Validation
boolean valid = StringUtils.isValidEmail("test@example.com");
boolean valid = StringUtils.isValidUrl("https://example.com");

// Masking
String masked = StringUtils.mask("1234567890", 2, '*');           // "12******90"
String masked = StringUtils.maskEmail("john@example.com");        // "jo***@ex***.com"

// Joining
String result = StringUtils.join(", ", "a", "b", "c");            // "a, b, c"
String result = StringUtils.join("-", Arrays.asList("a", "b"));   // "a-b"

// Character checks
boolean numeric = StringUtils.isNumeric("12345");
boolean alpha = StringUtils.isAlphabetic("hello");
boolean alphanum = StringUtils.isAlphanumeric("hello123");
```

#### DateUtils

Date and time operations:

```java
// Current date/time
LocalDateTime now = DateUtils.now();
LocalDate today = DateUtils.today();

// Conversions
Date date = DateUtils.toDate(localDateTime);
LocalDateTime dateTime = DateUtils.toLocalDateTime(date);

// Formatting
String formatted = DateUtils.format(dateTime);                    // "2023-10-15 10:30:45"
String formatted = DateUtils.format(date);                        // "2023-10-15"
String formatted = DateUtils.format(dateTime, "dd/MM/yyyy");     // "15/10/2023"

// Parsing
LocalDateTime dt = DateUtils.parseDateTime("2023-10-15 10:30:45");
LocalDate d = DateUtils.parseDate("2023-10-15");
LocalDateTime iso = DateUtils.parseIsoDateTime("2023-10-15T10:30:45");

// Calculations
long days = DateUtils.daysBetween(start, end);
long hours = DateUtils.hoursBetween(startTime, endTime);

// Checks
boolean past = DateUtils.isPast(date);
boolean future = DateUtils.isFuture(date);
boolean recent = DateUtils.isWithinLastHours(dateTime, 24);

// Arithmetic
LocalDate newDate = DateUtils.addDays(date, 5);
LocalDateTime newTime = DateUtils.addHours(dateTime, 3);

// Day boundaries
LocalDateTime start = DateUtils.startOfDay(dateTime);  // 00:00:00
LocalDateTime end = DateUtils.endOfDay(dateTime);      // 23:59:59.999999999

// Epoch conversions
LocalDateTime dt = DateUtils.fromEpochMilli(1697365845000L);
long epoch = DateUtils.toEpochMilli(dateTime);
```

#### ValidationUtils

Validation helpers that throw `ValidationException` on failure:

```java
// Null/Empty checks
ValidationUtils.notNull(obj, "fieldName");
ValidationUtils.notEmpty(str, "fieldName");
ValidationUtils.notBlank(str, "fieldName");
ValidationUtils.notEmpty(collection, "fieldName");

// String length
ValidationUtils.length(str, 5, 10, "password");
ValidationUtils.minLength(str, 8, "password");
ValidationUtils.maxLength(str, 100, "description");

// Number ranges
ValidationUtils.range(value, 1, 100, "rating");
ValidationUtils.min(value, 0, "price");
ValidationUtils.max(value, 1000, "quantity");
ValidationUtils.positive(value, "amount");
ValidationUtils.nonNegative(value, "count");

// Format validation
ValidationUtils.email(email, "email");
ValidationUtils.url(url, "website");
ValidationUtils.password(password, "password");  // 8+ chars, upper, lower, digit, special
ValidationUtils.pattern(str, Pattern.compile("[A-Z]{3}"), "code");

// Value checks
ValidationUtils.in(value, Arrays.asList("A", "B", "C"), "category");
ValidationUtils.equals(value1, value2, "password", "confirmPassword");

// Batch validation
Map<String, String> errors = new HashMap<>();
// ... populate errors ...
ValidationUtils.validate(errors);  // Throws if not empty
```

### Constants

#### ApiConstants

Common API constants:

```java
// API Versioning
ApiConstants.API_V1                // "/api/v1"
ApiConstants.API_V2                // "/api/v2"

// Headers
ApiConstants.HEADER_AUTHORIZATION  // "Authorization"
ApiConstants.HEADER_REQUEST_ID     // "X-Request-ID"
ApiConstants.HEADER_API_KEY        // "X-API-Key"

// Content Types
ApiConstants.CONTENT_TYPE_JSON     // "application/json"
ApiConstants.CONTENT_TYPE_XML      // "application/xml"

// Pagination
ApiConstants.DEFAULT_PAGE_NUMBER   // 0
ApiConstants.DEFAULT_PAGE_SIZE     // 20
ApiConstants.MAX_PAGE_SIZE         // 100
ApiConstants.PARAM_PAGE            // "page"
ApiConstants.PARAM_SIZE            // "size"
ApiConstants.PARAM_SORT            // "sort"

// Date Formats
ApiConstants.DATE_FORMAT           // "yyyy-MM-dd"
ApiConstants.DATE_TIME_FORMAT      // "yyyy-MM-dd'T'HH:mm:ss"
ApiConstants.ISO_8601_FORMAT       // "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

// Cache Keys
ApiConstants.CACHE_PREFIX          // "filmpire:"
ApiConstants.CACHE_MOVIES          // "filmpire:movies:"
ApiConstants.DEFAULT_CACHE_TTL     // 3600 (1 hour)

// Messages
ApiConstants.MSG_SUCCESS           // "Operation completed successfully"
ApiConstants.MSG_CREATED           // "Resource created successfully"
ApiConstants.MSG_UPDATED           // "Resource updated successfully"
ApiConstants.MSG_DELETED           // "Resource deleted successfully"

// Rate Limiting
ApiConstants.RATE_LIMIT_REQUESTS   // 100
ApiConstants.RATE_LIMIT_DURATION_SECONDS // 60
```

#### ErrorCodes

Standardized error codes:

```java
// Generic (1xxx)
ErrorCodes.INTERNAL_SERVER_ERROR   // "ERR_1000"
ErrorCodes.VALIDATION_ERROR        // "ERR_1001"
ErrorCodes.RESOURCE_NOT_FOUND      // "ERR_1002"
ErrorCodes.DUPLICATE_RESOURCE      // "ERR_1003"

// Authentication (2xxx)
ErrorCodes.UNAUTHORIZED            // "ERR_2000"
ErrorCodes.INVALID_CREDENTIALS     // "ERR_2001"
ErrorCodes.TOKEN_EXPIRED           // "ERR_2002"
ErrorCodes.TOKEN_INVALID           // "ERR_2003"

// Authorization (3xxx)
ErrorCodes.FORBIDDEN               // "ERR_3000"
ErrorCodes.INSUFFICIENT_PERMISSIONS // "ERR_3001"
ErrorCodes.ACCESS_DENIED           // "ERR_3002"

// User Service (4xxx)
ErrorCodes.USER_NOT_FOUND          // "ERR_4000"
ErrorCodes.EMAIL_ALREADY_EXISTS    // "ERR_4002"
ErrorCodes.PASSWORD_TOO_WEAK       // "ERR_4006"

// Movie Service (5xxx)
ErrorCodes.MOVIE_NOT_FOUND         // "ERR_5000"
ErrorCodes.GENRE_NOT_FOUND         // "ERR_5002"
ErrorCodes.TMDB_API_ERROR          // "ERR_5004"

// Actor Service (6xxx)
ErrorCodes.ACTOR_NOT_FOUND         // "ERR_6000"

// Media Service (7xxx)
ErrorCodes.FILE_UPLOAD_FAILED      // "ERR_7001"
ErrorCodes.FILE_TOO_LARGE          // "ERR_7002"
ErrorCodes.INVALID_FILE_TYPE       // "ERR_7003"

// AI Service (8xxx)
ErrorCodes.AI_SERVICE_ERROR        // "ERR_8000"
ErrorCodes.RECOMMENDATION_ERROR    // "ERR_8001"

// External Services (9xxx)
ErrorCodes.SERVICE_UNAVAILABLE     // "ERR_9000"
ErrorCodes.RATE_LIMIT_EXCEEDED     // "ERR_9004"
ErrorCodes.DATABASE_ERROR          // "ERR_9005"

// Get description
String desc = ErrorCodes.getDescription("ERR_1002");
```

### Annotations

#### @LogExecutionTime

Logs method execution time:

```java
@LogExecutionTime
public void expensiveOperation() {
    // Implementation
}

// With custom message and threshold
@LogExecutionTime(value = "Processing movie", level = LogLevel.WARN, thresholdMillis = 1000)
public void processMovie() {
    // Implementation
}
```

**Attributes:**
- `value` - Custom message prefix
- `level` - Log level (DEBUG, INFO, WARN, ERROR)
- `thresholdMillis` - Only log if execution exceeds this (ms)

#### @ValidateRequest

Enables automatic request validation:

```java
@ValidateRequest
@PostMapping("/users")
public ResponseEntity<User> createUser(@Valid @RequestBody UserDTO dto) {
    // Implementation
}

// With validation groups and fail-fast
@ValidateRequest(groups = {CreateGroup.class}, failFast = true)
public ResponseEntity<User> create(@Validated(CreateGroup.class) @RequestBody UserDTO dto) {
    // Implementation
}
```

**Attributes:**
- `groups` - Validation groups
- `failFast` - Stop on first error

#### @CacheResult

Caches method results:

```java
@CacheResult(key = "movie", ttl = 3600)
public Movie getMovieById(String id) {
    return movieRepository.findById(id).orElseThrow();
}

// With custom configuration
@CacheResult(
    key = "user:profile",
    ttl = 1800,
    timeUnit = TimeUnit.SECONDS,
    cacheNull = false,
    condition = "#userId != null"
)
public UserProfile getProfile(String userId) {
    return userService.findProfile(userId);
}
```

**Attributes:**
- `key` - Cache key prefix
- `ttl` - Time to live
- `timeUnit` - Time unit for TTL
- `cacheNull` - Cache null values
- `condition` - SpEL condition for caching

#### @RateLimited

Applies rate limiting:

```java
@RateLimited(requests = 100, per = 1, timeUnit = TimeUnit.MINUTES)
@GetMapping("/api/movies")
public ResponseEntity<List<Movie>> getMovies() {
    return ResponseEntity.ok(movieService.findAll());
}

// Per-user rate limiting
@RateLimited(
    requests = 10,
    per = 1,
    timeUnit = TimeUnit.MINUTES,
    keyPrefix = "api:search",
    perUser = true
)
@GetMapping("/api/search")
public ResponseEntity<?> search(@RequestParam String query) {
    return ResponseEntity.ok(searchService.search(query));
}
```

**Attributes:**
- `requests` - Number of allowed requests
- `per` - Time window duration
- `timeUnit` - Time unit for window
- `keyPrefix` - Rate limit key prefix
- `perUser` - Apply limit per user

## 🧪 Testing

### Run Tests

```bash
./gradlew :backend:shared-library:test
```

### Test Coverage

**Total: 96 tests, 100% passing**

- `ApiResponseTest` - 5 tests
- `PageResponseTest` - 5 tests
- `DateUtilsTest` - 27 tests
- `StringUtilsTest` - 22 tests
- `ValidationUtilsTest` - 37 tests

Coverage target: 100%

```bash
./gradlew :backend:shared-library:jacocoTestReport
```

Report: `build/reports/jacoco/test/html/index.html`

## 📚 Best Practices

### 1. Use ApiResponse Consistently

Always wrap responses in `ApiResponse` for consistency:

```java
✅ return ApiResponse.success(data);
❌ return ResponseEntity.ok(data);
```

### 2. Use Specific Exceptions

Throw specific exceptions with meaningful messages:

```java
✅ throw new ResourceNotFoundException("Movie", movieId);
❌ throw new RuntimeException("Not found");
```

### 3. Validate Early

Validate inputs at the controller/service boundary:

```java
public Movie createMovie(MovieDTO dto) {
    ValidationUtils.notBlank(dto.getTitle(), "title");
    ValidationUtils.notNull(dto.getReleaseDate(), "releaseDate");
    ValidationUtils.range(dto.getRating(), 0.0, 10.0, "rating");
    // ... proceed with business logic
}
```

### 4. Use Constants

Use constants instead of magic strings/numbers:

```java
✅ response.setStatus(ApiConstants.DEFAULT_PAGE_SIZE);
❌ response.setStatus(20);
```

### 5. Leverage Utility Methods

Use utility methods instead of custom implementations:

```java
✅ if (StringUtils.isBlank(email)) { ... }
❌ if (email == null || email.trim().isEmpty()) { ... }
```

## 📖 API Documentation

All classes and methods include comprehensive JavaDoc. Use your IDE's documentation viewer or generate JavaDoc:

```bash
./gradlew :backend:shared-library:javadoc
```

Documentation: `build/docs/javadoc/index.html`

## 🔧 Configuration

No configuration required - this is a pure Java library.

## 📦 Dependencies

- Spring Boot 3.5.8
- Spring Boot Starter Validation
- MapStruct 1.6.3
- Lombok 1.18.42

## 📝 Changelog

### Version 1.0.0 (2025-11-16)

- ✅ Initial release
- ✅ Common DTOs (ApiResponse, ErrorResponse, PageResponse)
- ✅ Custom exceptions (6 exception classes)
- ✅ Utility classes (DateUtils, StringUtils, ValidationUtils)
- ✅ Constants (ApiConstants, ErrorCodes)
- ✅ Annotations (4 custom annotations)
- ✅ 96 unit tests, 100% passing
- ✅ Bug fixes:
  - Fixed `maskEmail()` to handle short domain names (< 2 chars)
  - Fixed `truncateWithEllipsis()` to handle maxLength < 3
  - Fixed `toCamelCase()` to handle leading separators correctly
  - Enhanced test coverage with edge cases

## 🤝 Contributing

This library is used across all Filmpire microservices. When adding new functionality:

1. Ensure it's truly cross-cutting (used by 2+ services)
2. Write comprehensive tests
3. Add complete JavaDoc
4. Update this README

## 📄 License

Part of the Filmpire Microservices Platform - Portfolio Project

---

**Version:** 1.0.0-SNAPSHOT  
**Last Updated:** November 16, 2025
