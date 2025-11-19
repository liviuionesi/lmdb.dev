package com.filmpire.movie.repository;

import com.filmpire.movie.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * MongoDB repository for Movie entity.
 * Provides custom queries for movie discovery and search.
 */
@Repository
public interface MovieRepository extends MongoRepository<Movie, String> {

    /**
     * Find movie by TMDB ID.
     *
     * @param tmdbId TMDB movie ID
     * @return Optional movie
     */
    Optional<Movie> findByTmdbId(Long tmdbId);

    /**
     * Find movies by title (case-insensitive partial match).
     *
     * @param title Movie title
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    @Query("{ 'title': { $regex: ?0, $options: 'i' } }")
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    /**
     * Find movies by genre ID.
     *
     * @param genreId Genre ID
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    @Query("{ 'genres.id': ?0 }")
    Page<Movie> findByGenreId(Long genreId, Pageable pageable);

    /**
     * Find movies by release year.
     *
     * @param startDate Start of year
     * @param endDate End of year
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    Page<Movie> findByReleaseDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find movies with minimum vote average.
     *
     * @param minRating Minimum rating
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    Page<Movie> findByVoteAverageGreaterThanEqual(Double minRating, Pageable pageable);

    /**
     * Find all movies ordered by popularity descending.
     *
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    Page<Movie> findAllByOrderByPopularityDesc(Pageable pageable);

    /**
     * Find all movies ordered by vote average descending.
     *
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    Page<Movie> findAllByOrderByVoteAverageDesc(Pageable pageable);

    /**
     * Find all movies ordered by release date descending.
     *
     * @param pageable Pagination parameters
     * @return Page of movies
     */
    Page<Movie> findAllByOrderByReleaseDateDesc(Pageable pageable);

    /**
     * Check if movie exists by TMDB ID.
     *
     * @param tmdbId TMDB movie ID
     * @return true if exists
     */
    boolean existsByTmdbId(Long tmdbId);
}

