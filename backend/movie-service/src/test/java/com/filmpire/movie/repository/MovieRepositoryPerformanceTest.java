package com.filmpire.movie.repository;

import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.Movie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for MovieRepository using Testcontainers.
 */
@DataMongoTest
@Testcontainers
@DisplayName("MovieRepository Performance Tests")
class MovieRepositoryPerformanceTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    private final MovieRepository movieRepository;

    @org.springframework.beans.factory.annotation.Autowired
    MovieRepositoryPerformanceTest(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @AfterAll
    static void cleanup() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            mongoDBContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        movieRepository.deleteAll();
    }

    @Test
    @DisplayName("Should handle bulk insert efficiently")
    void bulkInsert_ShouldBeEfficient() {
        // Arrange
        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            movies.add(createTestMovie((long) i, "Movie " + i, 7.0 + (i % 3), 
                    LocalDate.of(2020, 1, 1).plusDays(i)));
        }

        // Act
        long startTime = System.currentTimeMillis();
        List<Movie> saved = movieRepository.saveAll(movies);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(saved).hasSize(100);
        assertThat(endTime - startTime).isLessThan(5000); // Should complete within 5 seconds
    }

    @Test
    @DisplayName("Should perform indexed queries efficiently")
    void indexedQuery_ShouldBeEfficient() {
        // Arrange - Insert 1000 movies
        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            movies.add(createTestMovie((long) i, "Movie " + i, 7.0 + (i % 3), 
                    LocalDate.of(2020, 1, 1).plusDays(i % 365)));
        }
        movieRepository.saveAll(movies);

        // Act - Query by indexed field (tmdbId)
        long startTime = System.currentTimeMillis();
        Optional<Movie> result = movieRepository.findByTmdbId(500L);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(result).isPresent();
        assertThat(endTime - startTime).isLessThan(100); // Should be very fast (< 100ms)
    }

    @Test
    @DisplayName("Should handle pagination efficiently with large datasets")
    void pagination_WithLargeDataset_ShouldBeEfficient() {
        // Arrange - Insert 500 movies
        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            Movie movie = createTestMovie((long) i, "Movie " + i, 7.0 + (i % 3), 
                    LocalDate.of(2020, 1, 1).plusDays(i % 365));
            movie.setPopularity(1000.0 - i); // Descending popularity
            movies.add(movie);
        }
        movieRepository.saveAll(movies);

        // Act - Paginate through results
        Pageable pageable = PageRequest.of(0, 20);
        long startTime = System.currentTimeMillis();
        Page<Movie> page1 = movieRepository.findAllByOrderByPopularityDesc(pageable);
        
        pageable = PageRequest.of(5, 20);
        Page<Movie> page6 = movieRepository.findAllByOrderByPopularityDesc(pageable);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(page1.getContent()).hasSize(20);
        assertThat(page6.getContent()).hasSize(20);
        assertThat(page1.getTotalElements()).isEqualTo(500);
        assertThat(endTime - startTime).isLessThan(1000); // Should complete within 1 second
    }

    @Test
    @DisplayName("Should handle complex genre queries efficiently")
    void genreQuery_WithComplexData_ShouldBeEfficient() {
        // Arrange - Insert movies with genres
        Genre actionGenre = Genre.builder().id(28L).name("Action").build();
        Genre dramaGenre = Genre.builder().id(18L).name("Drama").build();
        Genre comedyGenre = Genre.builder().id(35L).name("Comedy").build();

        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            Movie movie = createTestMovie((long) i, "Movie " + i, 7.0 + (i % 3), 
                    LocalDate.of(2020, 1, 1).plusDays(i % 365));
            
            // Assign genres based on ID
            if (i % 3 == 0) {
                movie.setGenres(Arrays.asList(actionGenre, dramaGenre));
            } else if (i % 3 == 1) {
                movie.setGenres(Arrays.asList(actionGenre));
            } else {
                movie.setGenres(Arrays.asList(comedyGenre));
            }
            movies.add(movie);
        }
        movieRepository.saveAll(movies);

        // Act - Query by genre
        Pageable pageable = PageRequest.of(0, 50);
        long startTime = System.currentTimeMillis();
        Page<Movie> actionMovies = movieRepository.findByGenreId(28L, pageable);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(actionMovies.getContent()).isNotEmpty();
        assertThat(endTime - startTime).isLessThan(500); // Should complete within 500ms
    }

    @Test
    @DisplayName("Should handle date range queries efficiently")
    void dateRangeQuery_ShouldBeEfficient() {
        // Arrange - Insert movies across date range
        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 365; i++) {
            movies.add(createTestMovie((long) i, "Movie " + i, 7.0 + (i % 3), 
                    LocalDate.of(2020, 1, 1).plusDays(i)));
        }
        movieRepository.saveAll(movies);

        // Act - Query date range
        LocalDate startDate = LocalDate.of(2020, 6, 1);
        LocalDate endDate = LocalDate.of(2020, 9, 30);
        Pageable pageable = PageRequest.of(0, 100);
        
        long startTime = System.currentTimeMillis();
        Page<Movie> result = movieRepository.findByReleaseDateBetween(startDate, endDate, pageable);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(result.getContent()).isNotEmpty();
        assertThat(endTime - startTime).isLessThan(500); // Should complete within 500ms
    }

    @Test
    @DisplayName("Should handle text search efficiently")
    void textSearch_ShouldBeEfficient() {
        // Arrange - Insert movies with various titles
        List<Movie> movies = new ArrayList<>();
        movies.add(createTestMovie(1L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));
        movies.add(createTestMovie(2L, "The Dark Knight Rises", 8.4, LocalDate.of(2012, 7, 20)));
        movies.add(createTestMovie(3L, "Fight Club", 8.8, LocalDate.of(1999, 10, 15)));
        movies.add(createTestMovie(4L, "Inception", 8.8, LocalDate.of(2010, 7, 16)));
        
        // Add noise data
        for (int i = 5; i <= 100; i++) {
            movies.add(createTestMovie((long) i, "Random Movie " + i, 7.0, 
                    LocalDate.of(2020, 1, 1)));
        }
        movieRepository.saveAll(movies);

        // Act - Search by title
        Pageable pageable = PageRequest.of(0, 10);
        long startTime = System.currentTimeMillis();
        Page<Movie> darkKnightMovies = movieRepository.findByTitleContainingIgnoreCase("dark knight", pageable);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(darkKnightMovies.getContent()).hasSize(2);
        assertThat(endTime - startTime).isLessThan(500); // Should complete within 500ms
    }

    @Test
    @DisplayName("Should handle existence checks efficiently")
    void existenceCheck_ShouldBeEfficient() {
        // Arrange
        List<Movie> movies = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            movies.add(createTestMovie((long) i, "Movie " + i, 7.0, LocalDate.of(2020, 1, 1)));
        }
        movieRepository.saveAll(movies);

        // Act - Check multiple existences
        long startTime = System.currentTimeMillis();
        boolean exists1 = movieRepository.existsByTmdbId(500L);
        boolean exists2 = movieRepository.existsByTmdbId(999L);
        boolean notExists = movieRepository.existsByTmdbId(9999L);
        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(exists1).isTrue();
        assertThat(exists2).isTrue();
        assertThat(notExists).isFalse();
        assertThat(endTime - startTime).isLessThan(100); // Should be very fast
    }

    // Helper method

    private Movie createTestMovie(Long tmdbId, String title, Double voteAverage, LocalDate releaseDate) {
        return Movie.builder()
                .tmdbId(tmdbId)
                .title(title)
                .overview("Test overview for " + title)
                .posterPath("/poster" + tmdbId + ".jpg")
                .backdropPath("/backdrop" + tmdbId + ".jpg")
                .releaseDate(releaseDate)
                .voteAverage(voteAverage)
                .voteCount(1000)
                .runtime(120)
                .status("Released")
                .budget(50000000L)
                .revenue(100000000L)
                .popularity(100.0 + tmdbId)
                .adult(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tmdbSyncVersion(1)
                .build();
    }
}

