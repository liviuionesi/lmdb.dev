package dev.lmdb.ai.service;

import dev.lmdb.ai.client.CandidateMovie;
import dev.lmdb.ai.client.MovieCatalogClient;
import dev.lmdb.ai.dto.MovieRecommendationDto;
import dev.lmdb.ai.dto.RecommendationRequestDto;
import dev.lmdb.ai.dto.RecommendationResponseDto;
import dev.lmdb.ai.model.UserTasteProfile;
import dev.lmdb.ai.repository.UserTasteProfileRepository;
import dev.lmdb.ai.security.PromptSanitizer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Movie recommendations computed from LMDB's own catalog — never proxied from TMDB's own
 * recommendation endpoint. Candidates come from {@link MovieCatalogClient} (movie-service's
 * persisted data); the assistant ranks and explains them against the user's recent viewing.
 *
 * <p>As a side effect, this also refreshes the user's {@link UserTasteProfile} embedding from their
 * recent-movies text, which is what backs the semantic search / "similar users" feature ({@link
 * SemanticSearchService}) — a taste profile only exists once a user has asked for recommendations
 * at least once.
 */
@Service
@Slf4j
public class RecommendationService {

  private static final String SYSTEM_PROMPT =
      """
        You are LMDB's recommendation engine. You will be given a list of
        candidate movies and a user's recently watched movies. Pick the best
        matches from the CANDIDATES ONLY — never invent a movie not listed —
        and explain briefly why each fits the user's taste. Respond with a
        JSON array of objects: movieId (the candidate's id, as a string),
        score (0 to 1), reason (one sentence).
        """;

  private final ChatClient chatClient;
  private final EmbeddingModel embeddingModel;
  private final MovieCatalogClient movieCatalogClient;
  private final UserTasteProfileRepository tasteProfileRepository;

  /**
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to rank and explain
   *     candidates
   * @param embeddingModel model used to embed the user's recent-movies text into a taste vector
   * @param movieCatalogClient source of candidate movies from movie-service's own catalog
   * @param tasteProfileRepository persistence for the resulting {@link UserTasteProfile}
   */
  public RecommendationService(
      ChatClient.Builder chatClientBuilder,
      EmbeddingModel embeddingModel,
      MovieCatalogClient movieCatalogClient,
      UserTasteProfileRepository tasteProfileRepository) {
    this.chatClient = chatClientBuilder.build();
    this.embeddingModel = embeddingModel;
    this.movieCatalogClient = movieCatalogClient;
    this.tasteProfileRepository = tasteProfileRepository;
  }

  /**
   * Generates ranked, explained recommendations for a user.
   *
   * @param request the user, their recent movies, and how many recommendations to return
   * @return the ranked recommendation list; empty if movie-service has no candidates to offer
   */
  @Transactional
  public RecommendationResponseDto recommend(RecommendationRequestDto request) {
    List<CandidateMovie> candidates =
        movieCatalogClient.fetchCandidates(request.countOrDefault() * 3);
    refreshTasteProfile(request);

    if (candidates.isEmpty()) {
      log.warn("No recommendation candidates available for user {}", request.userId());
      return new RecommendationResponseDto(List.of());
    }

    // Catalog text is ultimately TMDB-sourced free text, not something this service authored, so
    // it gets the same treatment as caller-supplied input: flattened to one line so it cannot open
    // a new field, and quote-escaped so it cannot close the one it sits in.
    String candidateList =
        candidates.stream()
            .map(
                c ->
                    "id=%d title=\"%s\" overview=\"%s\" voteAverage=%s"
                        .formatted(
                            c.tmdbId(),
                            promptField(c.title()),
                            promptField(c.overview()),
                            c.voteAverage()))
            .reduce("", (a, b) -> a + b + "\n");

    String userPrompt =
        """
            Recently watched: %s
            Candidates:
            %s
            Return exactly %d recommendations.
            """
            .formatted(
                String.join(", ", PromptSanitizer.sanitizeAll(request.recentMovies())),
                candidateList,
                request.countOrDefault());

    List<MovieRecommendationDto> recommendations =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .entity(new ParameterizedTypeReference<List<MovieRecommendationDto>>() {});

    return new RecommendationResponseDto(recommendations == null ? List.of() : recommendations);
  }

  /**
   * Prepares one piece of catalog text for interpolation into a quoted prompt field.
   *
   * @param value the catalog text, possibly {@code null}
   * @return the value flattened to a single line and with double quotes replaced by single ones, so
   *     it can neither break out of its own {@code key="value"} field nor start a new line
   */
  private static String promptField(String value) {
    return PromptSanitizer.sanitize(value).replace('"', '\'');
  }

  /**
   * Embeds the user's recent-movies text and upserts it as their taste profile — the persisted
   * artifact semantic search runs its ANN query against. Does nothing if the request carries no
   * recent movies to embed.
   *
   * @param request the recommendation request whose {@code recentMovies} are embedded
   */
  private void refreshTasteProfile(RecommendationRequestDto request) {
    List<String> recentMovies = PromptSanitizer.sanitizeAll(request.recentMovies());
    if (recentMovies.isEmpty()) {
      return;
    }
    float[] embedding = embeddingModel.embed(String.join(", ", recentMovies));
    UserTasteProfile profile =
        UserTasteProfile.builder()
            .userId(request.userId())
            .embedding(embedding)
            .featureWeights(Map.of())
            .lastUpdated(Instant.now())
            .build();
    tasteProfileRepository.save(profile);
  }
}
