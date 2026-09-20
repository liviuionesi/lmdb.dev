package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.SearchResultMovieDto;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

/**
 * Asks the chat model which movies in a list fit a search query. {@link QueryAggregationService}
 * uses it for the parts of a query that data lookups cannot check, such as a genre on a person's
 * filmography or a keyword like "heist".
 *
 * <p>The check is an extra filter, so a model failure never fails the search: the movies come back
 * unchanged.
 *
 * <p>Collaborates with {@link ChatClient} (Ollama) and is called by {@link
 * QueryAggregationService}.
 */
@Service
@Slf4j
public class RelevanceFilterService {

  /** Movies per model call. A small model reads a short list better than a long one. */
  static final int CHUNK_SIZE = 40;

  /** Longest overview text sent per movie, in characters. */
  private static final int OVERVIEW_LIMIT = 120;

  private static final String SYSTEM_PROMPT =
      """
        You check which movies fit a movie search query.
        You get the query and a numbered list of movies, each with a year and a short
        overview. Put the numbers of the movies that fit the whole query in "matches".
        Use what you know about each movie, not just its title.
        If a movie might fit and you are not sure, include it.

        Respond with exactly one JSON object matching the target schema.
      """;

  private final ChatClient chatClient;

  /**
   * Builds the service on the shared chat client builder.
   *
   * @param chatClientBuilder builder for the Ollama-backed chat client
   */
  public RelevanceFilterService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  /**
   * The model's answer: the list numbers, starting at 1, of the movies that fit.
   *
   * @param matches numbers of the movies that fit the query
   */
  public record RelevanceVerdict(List<Integer> matches) {}

  /**
   * Keeps the movies that fit the query.
   *
   * @param query the user's search text
   * @param candidates the movies to check
   * @return the movies the model says fit, in their original order; all of them for any chunk the
   *     model could not judge
   */
  public List<SearchResultMovieDto> filter(String query, List<SearchResultMovieDto> candidates) {
    List<SearchResultMovieDto> kept = new ArrayList<>();
    for (int start = 0; start < candidates.size(); start += CHUNK_SIZE) {
      int end = Math.min(start + CHUNK_SIZE, candidates.size());
      kept.addAll(filterChunk(query, candidates.subList(start, end)));
    }
    return kept;
  }

  /**
   * Checks one chunk with one model call.
   *
   * @param query the user's search text
   * @param chunk at most {@link #CHUNK_SIZE} movies
   * @return the chunk's movies that fit; the whole chunk if the call or the reply fails
   */
  private List<SearchResultMovieDto> filterChunk(String query, List<SearchResultMovieDto> chunk) {
    try {
      RelevanceVerdict verdict =
          chatClient
              .prompt()
              .system(SYSTEM_PROMPT)
              // No randomness: the same query should always be read the same way.
              .options(ChatOptions.builder().temperature(0.0))
              .user(describe(query, chunk))
              .call()
              .entity(RelevanceVerdict.class);
      if (verdict == null || verdict.matches() == null) {
        return chunk;
      }
      // 1. Numbers start at 1. Sorting keeps the original order and drops repeats' effect;
      //    numbers outside the list are model mistakes and are ignored.
      return verdict.matches().stream()
          .filter(number -> number != null && number >= 1 && number <= chunk.size())
          .distinct()
          .sorted()
          .map(number -> chunk.get(number - 1))
          .toList();
    } catch (Exception e) {
      log.warn("Relevance check failed, keeping every movie in the chunk: {}", e.getMessage());
      return chunk;
    }
  }

  /**
   * Writes the user message: the query, then the numbered movies.
   *
   * @param query the user's search text
   * @param chunk the movies to list
   * @return the message text
   */
  private static String describe(String query, List<SearchResultMovieDto> chunk) {
    StringBuilder text = new StringBuilder("Query: ").append(query).append("\n\nMovies:\n");
    for (int i = 0; i < chunk.size(); i++) {
      SearchResultMovieDto movie = chunk.get(i);
      text.append(i + 1).append(". ").append(movie.title()).append(" (").append(yearOf(movie));
      text.append(")");
      String overview = movie.overview();
      if (overview != null && !overview.isBlank()) {
        text.append(" - ").append(overview, 0, Math.min(overview.length(), OVERVIEW_LIMIT));
      }
      text.append("\n");
    }
    return text.toString();
  }

  /**
   * Reads the release year for the prompt.
   *
   * @param movie a movie with an optional release date
   * @return the four-digit year, or "year unknown"
   */
  private static String yearOf(SearchResultMovieDto movie) {
    String date = movie.releaseDate();
    return date != null && date.length() >= 4 ? date.substring(0, 4) : "year unknown";
  }
}
