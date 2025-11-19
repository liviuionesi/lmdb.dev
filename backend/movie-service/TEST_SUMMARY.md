# Movie Service - Comprehensive Test Suite

## Overview
This document describes the comprehensive test suite created for the Movie Service. The test suite includes unit tests, integration tests, performance tests, and end-to-end tests covering all aspects of the service.

## Test Structure

### 1. Controller Tests

#### MovieControllerTest.java
- **Purpose**: Unit tests for REST API endpoints
- **Coverage**: All MovieController endpoints with mocked service layer
- **Test Count**: 12 tests
- **Key Tests**:
  - GET /api/v1/movies/{id} - Movie details retrieval
  - GET /api/v1/movies/discover - Movie discovery with filters
  - GET /api/v1/movies/search - Movie search functionality
  - GET /api/v1/movies/trending - Trending movies (day/week)
  - GET /api/v1/movies/popular - Popular movies
  - GET /api/v1/movies/top-rated - Top-rated movies
  - GET /api/v1/movies/{id}/videos - Movie videos/trailers
  - GET /api/v1/movies/{id}/credits - Movie credits (cast/crew)
  - GET /api/v1/movies/{id}/similar - Similar movies
  - GET /api/v1/movies/{id}/recommendations - Movie recommendations

#### GenreControllerTest.java
- **Purpose**: Unit tests for Genre API endpoints
- **Coverage**: Genre list retrieval
- **Test Count**: 2 tests
- **Key Tests**:
  - GET /api/v1/genres - All genres retrieval
  - Empty genres list handling

#### MovieControllerErrorTest.java
- **Purpose**: Error handling and exception scenarios
- **Coverage**: HTTP error responses (404, 503, 400, 500)
- **Test Count**: 9 tests
- **Key Tests**:
  - ResourceNotFoundException handling (404)
  - ServiceUnavailableException handling (503)
  - Generic exception handling (500)
  - Missing required parameters (400)
  - Invalid path variables (400)

### 2. Service Layer Tests

#### MovieServiceTest.java
- **Purpose**: Unit tests for business logic
- **Coverage**: All service methods with mocked dependencies
- **Test Count**: 13 tests
- **Key Tests**:
  - Movie retrieval from MongoDB cache
  - Movie fetching from TMDB API
  - Discover movies with various filters
  - Search functionality
  - Trending, popular, and top-rated movies
  - Videos and credits retrieval
  - Similar and recommended movies
  - Genre list retrieval

#### MovieServiceCacheTest.java
- **Purpose**: Tests caching behavior (MongoDB + Redis)
- **Coverage**: Hybrid caching strategy
- **Test Count**: 5 tests
- **Key Tests**:
  - MongoDB cache hits (no API calls)
  - Cache misses (API calls and storage)
  - Concurrent request handling
  - Data persistence after API fetch

### 3. Repository Tests

#### MovieRepositoryTest.java
- **Purpose**: Integration tests with MongoDB (Testcontainers)
- **Coverage**: All repository methods
- **Test Count**: 10 tests
- **Key Tests**:
  - Save and find by TMDB ID
  - Title search (case-insensitive)
  - Genre-based queries
  - Date range queries
  - Vote average filtering
  - Sorting (popularity, rating, date)
  - Existence checks

#### MovieRepositoryPerformanceTest.java
- **Purpose**: Performance tests with large datasets
- **Coverage**: Repository performance under load
- **Test Count**: 7 tests
- **Key Tests**:
  - Bulk insert (100 movies)
  - Indexed queries (1000 movies)
  - Pagination (500 movies)
  - Complex genre queries (200 movies)
  - Date range queries (365 movies)
  - Text search efficiency
  - Existence check performance

### 4. Mapper Tests

#### MovieMapperTest.java
- **Purpose**: Tests MapStruct mappings
- **Coverage**: Entity ↔ DTO conversions
- **Test Count**: 10 tests
- **Key Tests**:
  - Movie to MovieDto mapping
  - Movie to MovieListDto mapping
  - Genre, Video, Cast, Crew mappings
  - List conversions
  - Null value handling

### 5. Model Tests

#### MovieTest.java
- **Purpose**: Entity model validation
- **Coverage**: Movie entity behavior
- **Test Count**: 10 tests
- **Key Tests**:
  - Builder pattern
  - Null value handling
  - Getters and setters
  - Equals and hashCode
  - Genres and production companies
  - Spoken languages
  - Timestamps
  - Large numeric values
  - Edge case ratings

#### MovieDtoTest.java
- **Purpose**: DTO validation
- **Coverage**: MovieDto behavior
- **Test Count**: 6 tests
- **Key Tests**:
  - Builder pattern
  - Serialization/deserialization
  - Equals and hashCode
  - Genre handling
  - Null values
  - ToString method

### 6. Integration Tests

#### MovieServiceIntegrationTest.java
- **Purpose**: Full-stack integration tests
- **Coverage**: Controller → Service → Repository → MongoDB
- **Test Count**: 6 tests
- **Key Tests**:
  - Genre list retrieval (full flow)
  - Complete movie workflow (search, popular, trending)
  - Discover with filters
  - Pagination across multiple pages
  - Concurrent request handling
  - API response structure consistency

#### EndToEndIntegrationTest.java
- **Purpose**: End-to-end workflow tests
- **Coverage**: Complete user workflows
- **Test Count**: 10 tests
- **Key Tests**:
  - Complete movie discovery workflow
  - Search and details workflow
  - Filter and discover workflow
  - API response consistency
  - Pagination workflow
  - MongoDB caching behavior
  - Error handling workflow
  - Concurrent requests
  - All movie list endpoints

### 7. Application Tests

#### MovieServiceApplicationTest.java
- **Purpose**: Spring Boot application context tests
- **Coverage**: Application startup and bean configuration
- **Test Count**: 2 tests
- **Key Tests**:
  - Application context loads successfully
  - All required beans are present

## Test Configuration

### application-test.yml
- Disables Eureka client
- Disables Spring Cloud Config
- Uses Testcontainers for MongoDB
- Disables Redis cache for most tests
- Configures TMDB API (uses env variable or test key)

## Test Statistics

### Total Tests: ~95 test methods

### Coverage by Layer:
- **Controllers**: 23 tests
- **Services**: 18 tests
- **Repositories**: 17 tests
- **Mappers**: 10 tests
- **Models/DTOs**: 16 tests
- **Integration**: 16 tests

### Test Types:
- **Unit Tests**: ~60 tests
- **Integration Tests**: ~30 tests
- **Performance Tests**: 7 tests
- **E2E Tests**: 10 tests

## Technologies Used

- **JUnit 5**: Test framework
- **Mockito**: Mocking framework
- **AssertJ**: Fluent assertions
- **Spring Boot Test**: Spring testing support
- **MockMvc**: REST API testing
- **Testcontainers**: MongoDB containerization
- **MapStruct**: Entity-DTO mapping

## Running Tests

### Run all tests:
```bash
./gradlew test
```

### Run specific test class:
```bash
./gradlew test --tests MovieControllerTest
```

### Run integration tests only:
```bash
./gradlew test --tests '*IntegrationTest'
```

### Run with coverage:
```bash
./gradlew test jacocoTestReport
```

## Prerequisites

### For Repository Integration Tests:
- Docker/Podman running (for Testcontainers)
- Podman socket active: `systemctl --user start podman.socket`

### For Full API Tests:
- TMDB_API_KEY environment variable set (optional - tests will use mocks if not set)

## Test Best Practices Implemented

1. **Arrange-Act-Assert Pattern**: All tests follow AAA structure
2. **Descriptive Names**: Test methods have clear, descriptive names
3. **DisplayName Annotations**: Human-readable test descriptions
4. **Test Isolation**: Each test is independent and isolated
5. **Mocking Strategy**: Proper use of mocks for unit tests
6. **Integration Testing**: Real database integration with Testcontainers
7. **Performance Testing**: Validates performance under load
8. **Edge Cases**: Tests handle null values, empty lists, and boundary conditions
9. **Error Scenarios**: Comprehensive error handling tests
10. **Documentation**: Well-documented test purposes and coverage

## Key Features Tested

✅ Movie retrieval by ID
✅ Movie search functionality
✅ Movie discovery with filters (genre, year, rating)
✅ Trending movies (day/week)
✅ Popular movies
✅ Top-rated movies
✅ Movie videos/trailers
✅ Movie credits (cast/crew)
✅ Similar movies
✅ Recommended movies
✅ Genre list retrieval
✅ Pagination
✅ Caching (MongoDB + Redis)
✅ Error handling
✅ Data validation
✅ Performance under load

## Notes

- Tests are designed to work with or without a TMDB API key
- Integration tests use Testcontainers for real MongoDB instances
- Performance tests validate sub-second response times
- All tests are self-contained and can run in parallel
- Comprehensive coverage of happy paths and error scenarios

## Continuous Integration

These tests are designed to run in CI/CD pipelines:
- No external dependencies (other than Docker/Podman)
- Automatic database setup/teardown
- Fast execution time
- Comprehensive coverage
- Clear failure messages

