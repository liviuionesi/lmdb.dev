# Shared Library

Common utilities, DTOs, and constants shared across all Filmpire microservices.

## Purpose

Provides reusable components to avoid code duplication across services.

## Contents

### Exception Classes
- `FilmpireException` - Base exception
- `ResourceNotFoundException` - 404 errors
- `ValidationException` - Input validation errors
- `ServiceException` - Internal service errors

### Common DTOs
- `ErrorResponse` - Standardized error format
- `PageResponse<T>` - Paginated response wrapper
- `ApiResponse<T>` - Generic API response

### Utility Classes
- `DateUtils` - Date/time utilities
- `StringUtils` - String manipulation
- `ValidationUtils` - Input validation helpers
- `JsonUtils` - JSON serialization/deserialization

### Constants
- `ApiConstants` - API-related constants
- `MessageConstants` - Error/success messages
- `RegexConstants` - Common regex patterns

## Usage

Add dependency in service's `build.gradle`:

```groovy
dependencies {
    implementation project(':backend:shared-library')
}
```

## Example

```java
import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.dto.ErrorResponse;
import com.filmpire.shared.util.ValidationUtils;

public class MovieService {
    public MovieDTO getMovie(String id) {
        ValidationUtils.requireNonBlank(id, "Movie ID");
        
        return movieRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Movie", "id", id
            ));
    }
}
```

## Testing

```bash
./gradlew :backend:shared-library:test
```

## Adding New Utilities

1. Create class in appropriate package
2. Add comprehensive Javadoc
3. Write unit tests (>90% coverage)
4. Update this README

