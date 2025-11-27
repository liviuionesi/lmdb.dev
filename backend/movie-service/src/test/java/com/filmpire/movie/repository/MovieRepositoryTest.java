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
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MovieRepository using Testcontainers.
 */
@DataMongoTest
@Testcontainers
@DisplayName("MovieRepository Integration Tests")
class MovieRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    private final MovieRepository movieRepository;

    @org.springframework.beans.factory.annotation.Autowired
    MovieRepositoryTest(MovieRepository movieRepository) {
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
    @DisplayName("Should save and find movie by TMDB ID")
    void saveAndFindByTmdbId_ShouldWork() {
        // Arrange
        Movie movie = createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15));

        // Act
        Movie saved = movieRepository.save(movie);
        Optional<Movie> found = movieRepository.findByTmdbId(550L);

        // Assert
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getTmdbId()).isEqualTo(550L);
        assertThat(found.get().getTitle()).isEqualTo("Fight Club");
    }

    @Test
    @DisplayName("Should find movies by title containing ignore case")
    void findByTitleContainingIgnoreCase_ShouldReturnMatches() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));
        movieRepository.save(createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6)));
        movieRepository.save(createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findByTitleContainingIgnoreCase("fight", pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fight Club");
    }

    @Test
    @DisplayName("Should find movies by genre ID")
    void findByGenreId_ShouldReturnMoviesWithGenre() {
        // Arrange
        Genre actionGenre = Genre.builder().id(28L).name("Action").build();
        Genre dramaGenre = Genre.builder().id(18L).name("Drama").build();

        Movie movie1 = createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15));
        movie1.setGenres(Arrays.asList(dramaGenre));
        
        Movie movie2 = createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18));
        movie2.setGenres(Arrays.asList(actionGenre, dramaGenre));

        Movie movie3 = createTestMovie(99L, "Mad Max", 8.1, LocalDate.of(2015, 5, 15));
        movie3.setGenres(Arrays.asList(actionGenre));

        movieRepository.saveAll(Arrays.asList(movie1, movie2, movie3));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findByGenreId(28L, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("The Dark Knight", "Mad Max");
    }

    @Test
    @DisplayName("Should find movies by release date between")
    void findByReleaseDateBetween_ShouldReturnMoviesInRange() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));
        movieRepository.save(createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6)));
        movieRepository.save(createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));

        LocalDate startDate = LocalDate.of(1995, 1, 1);
        LocalDate endDate = LocalDate.of(2000, 12, 31);
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findByReleaseDateBetween(startDate, endDate, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fight Club");
    }

    @Test
    @DisplayName("Should find movies by minimum vote average")
    void findByVoteAverageGreaterThanEqual_ShouldReturnHighRatedMovies() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));
        movieRepository.save(createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6)));
        movieRepository.save(createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));
        movieRepository.save(createTestMovie(99L, "Average Movie", 6.5, LocalDate.of(2010, 5, 1)));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findByVoteAverageGreaterThanEqual(8.5, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Forrest Gump", "The Dark Knight");
    }

    @Test
    @DisplayName("Should find all movies ordered by popularity desc")
    void findAllByOrderByPopularityDesc_ShouldReturnOrderedMovies() {
        // Arrange
        Movie movie1 = createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15));
        movie1.setPopularity(450.5);

        Movie movie2 = createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6));
        movie2.setPopularity(320.3);

        Movie movie3 = createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18));
        movie3.setPopularity(890.7);

        movieRepository.saveAll(Arrays.asList(movie1, movie2, movie3));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findAllByOrderByPopularityDesc(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("The Dark Knight");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Fight Club");
        assertThat(result.getContent().get(2).getTitle()).isEqualTo("Forrest Gump");
    }

    @Test
    @DisplayName("Should find all movies ordered by vote average desc")
    void findAllByOrderByVoteAverageDesc_ShouldReturnOrderedMovies() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));
        movieRepository.save(createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6)));
        movieRepository.save(createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findAllByOrderByVoteAverageDesc(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("The Dark Knight");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Forrest Gump");
        assertThat(result.getContent().get(2).getTitle()).isEqualTo("Fight Club");
    }

    @Test
    @DisplayName("Should find all movies ordered by release date desc")
    void findAllByOrderByReleaseDateDesc_ShouldReturnOrderedMovies() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));
        movieRepository.save(createTestMovie(13L, "Forrest Gump", 8.8, LocalDate.of(1994, 7, 6)));
        movieRepository.save(createTestMovie(155L, "The Dark Knight", 9.0, LocalDate.of(2008, 7, 18)));

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Movie> result = movieRepository.findAllByOrderByReleaseDateDesc(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("The Dark Knight");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Fight Club");
        assertThat(result.getContent().get(2).getTitle()).isEqualTo("Forrest Gump");
    }

    @Test
    @DisplayName("Should check if movie exists by TMDB ID")
    void existsByTmdbId_ShouldReturnCorrectValue() {
        // Arrange
        movieRepository.save(createTestMovie(550L, "Fight Club", 8.4, LocalDate.of(1999, 10, 15)));

        // Act
        boolean exists = movieRepository.existsByTmdbId(550L);
        boolean notExists = movieRepository.existsByTmdbId(999L);

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should handle empty results for non-existent TMDB ID")
    void findByTmdbId_WhenNotExists_ShouldReturnEmpty() {
        // Act
        Optional<Movie> result = movieRepository.findByTmdbId(999L);

        // Assert
        assertThat(result).isEmpty();
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
                .popularity(100.0)
                .adult(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tmdbSyncVersion(1)
                .build();
    }
}
