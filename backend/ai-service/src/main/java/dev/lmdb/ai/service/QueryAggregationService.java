package dev.lmdb.ai.service;

import dev.lmdb.ai.client.ActorCatalogClient;
import dev.lmdb.ai.client.MovieCatalogClient;
import dev.lmdb.ai.client.MovieListItem;
import dev.lmdb.ai.client.PersonCredit;
import dev.lmdb.ai.dto.NaturalLanguageSearchResponseDto;
import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.SearchResultMovieDto;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Executes a structured query filter (or plain-title fallback) across actor-service and
 * movie-service, merging results in memory (#203, ADR-020) — no database, no cross-service join
 * (ADR-002). Extraction is a separate collaborator ({@link QueryParsingService}, #202); this class
 * consumes its output and is the one that actually calls the downstream services.
 *
 * <p><b>Scoped to this Task's acceptance criteria — deliberate, stated gaps, not oversights:</b>
 *
 * <ul>
 *   <li>{@code genre} is part of ADR-020's filter shape but not handled here — no acceptance
 *       criterion for this Task covers it, and neither actor-service's credit data nor this class's
 *       year-range path carries a genre signal. Revisit if a future Task needs it.
 *   <li>{@code negated} is honored for {@code "role"} only, matching ADR-020's own stated
 *       field-grained limitation and #202's actual extraction behavior — no other field is ever
 *       negated by the extraction step today.
 *   <li>A query resolving to neither a person name nor a plain title (structurally possible per
 *       {@link StructuredQueryFilterDto}'s shape, though not one #202's own prompt is designed to
 *       produce) degrades to an empty result rather than an error — nothing to anchor an
 *       actor-service lookup on.
 *   <li>A negated {@code ACTED} role ("not starring X") degrades to an empty result rather than
 *       excluding X's real filmography — actor-service has no "movies X is absent from" signal to
 *       build a candidate set from, unlike a negated {@code DIRECTED}/{@code PRODUCED} role, which
 *       has a natural cast-filmography-minus-crew-credits interpretation. See {@link
 *       #resolvePersonCredits}'s own comment for why silently falling through to X's unfiltered
 *       filmography (an earlier version of this method's actual bug, caught in review) would be the
 *       literal opposite of what the query means, not just an unimplemented case.
 * </ul>
 */
@Service
@Slf4j
public class QueryAggregationService {

  /**
   * Bound on how many movie-service {@code /discover} results back a year-range constraint,
   * matching {@link MovieCatalogClient#discoverMovieIdsInYearRange}'s own documented reasoning.
   */
  private static final int YEAR_RANGE_RESULT_CAP = 200;

  private final QueryParsingService queryParsingService;
  private final ActorCatalogClient actorCatalogClient;
  private final MovieCatalogClient movieCatalogClient;

  /**
   * @param queryParsingService extracts a structured filter from the raw query (#202)
   * @param actorCatalogClient resolves the person/role side of the filter against actor-service
   * @param movieCatalogClient resolves the plain-title fallback and year-range side against
   *     movie-service
   */
  public QueryAggregationService(
      QueryParsingService queryParsingService,
      ActorCatalogClient actorCatalogClient,
      MovieCatalogClient movieCatalogClient) {
    this.queryParsingService = queryParsingService;
    this.actorCatalogClient = actorCatalogClient;
    this.movieCatalogClient = movieCatalogClient;
  }

  /**
   * Parses and executes one natural-language query end to end.
   *
   * @param rawQuery the caller-supplied query text, typed or transcribed from dictation
   * @return the matching movies — from movie-service's title search for a plain-title query, or
   *     aggregated across actor-service/movie-service for a structured one
   */
  public NaturalLanguageSearchResponseDto search(String rawQuery) {
    StructuredQueryFilterDto filter = queryParsingService.parse(rawQuery);

    if (filter.plainTitle() != null) {
      log.info("Executing plain-title fallback search");
      return new NaturalLanguageSearchResponseDto(toSearchResults(filter.plainTitle()));
    }

    log.info("Executing structured filter search: role={}", filter.role());
    return new NaturalLanguageSearchResponseDto(executeStructuredFilter(filter));
  }

  /**
   * Delegates to movie-service's existing title search, unchanged (#198 AC3, #203 AC4) — mapped
   * into this endpoint's own result shape, not movie-service's.
   *
   * @param title the literal title text
   * @return matching movies, most relevant first as movie-service ranks them
   */
  private List<SearchResultMovieDto> toSearchResults(String title) {
    return movieCatalogClient.searchByTitle(title, YEAR_RANGE_RESULT_CAP).stream()
        .map(QueryAggregationService::toSearchResult)
        .toList();
  }

  /**
   * Resolves a structured filter into a movie set, per #203's acceptance criteria: person + role +
   * year range (AC1), collaborator AND-narrowing (AC2), role negation (AC3).
   *
   * @param filter the structured filter to execute
   * @return matching movies; empty if the filter cannot be resolved (no person named, an unknown
   *     person/collaborator, or nothing survives the applied constraints) — never an error
   */
  private List<SearchResultMovieDto> executeStructuredFilter(StructuredQueryFilterDto filter) {
    if (filter.personName() == null) {
      log.warn("Structured filter names no person and carries no plain title — nothing to resolve");
      return List.of();
    }

    Optional<Long> personId = actorCatalogClient.findPersonId(filter.personName());
    if (personId.isEmpty()) {
      log.info("No actor-service match for the named person — zero results, not an error");
      return List.of();
    }

    // 1. Base candidate set: role-appropriate credits for the primary person, keyed by movie id so
    //    the later steps can intersect/subtract by simple Set operations.
    Map<Long, SearchResultMovieDto> candidates = resolvePersonCredits(personId.get(), filter);
    if (candidates.isEmpty()) {
      return List.of();
    }

    // 2. Year range: intersect against movie-service's discover results for that range (#218).
    if (filter.yearFrom() != null || filter.yearTo() != null) {
      Set<Long> inRange =
          movieCatalogClient.discoverMovieIdsInYearRange(
              filter.yearFrom(), filter.yearTo(), YEAR_RANGE_RESULT_CAP);
      candidates.keySet().retainAll(inRange);
      if (candidates.isEmpty()) {
        return List.of();
      }
    }

    // 3. Collaborators: AND semantics — each named collaborator must also be credited (as cast) on
    //    the same movie, so intersect the running set with their own filmography's movie ids. An
    //    unresolvable collaborator makes the whole AND unsatisfiable, not a constraint to skip.
    for (String collaborator : filter.collaborators()) {
      Optional<Long> collaboratorId = actorCatalogClient.findPersonId(collaborator);
      if (collaboratorId.isEmpty()) {
        log.info(
            "No actor-service match for collaborator '{}' — AND constraint unsatisfiable",
            collaborator);
        return List.of();
      }
      Set<Long> collaboratorMovieIds =
          toMovieIds(actorCatalogClient.fetchCastCredits(collaboratorId.get()));
      candidates.keySet().retainAll(collaboratorMovieIds);
      if (candidates.isEmpty()) {
        return List.of();
      }
    }

    return List.copyOf(candidates.values());
  }

  /**
   * Resolves the primary person's role-appropriate credits into the starting candidate map — the
   * one step that reads {@code role} and {@code negated} together, since negation only changes how
   * this step is computed, nothing downstream.
   *
   * @param personId the primary person's TMDB id
   * @param filter the structured filter (only {@code role}/{@code negated} are read here)
   * @return credits keyed by movie id, preserving insertion order for a stable result order
   */
  private Map<Long, SearchResultMovieDto> resolvePersonCredits(
      Long personId, StructuredQueryFilterDto filter) {
    boolean roleNegated = filter.negated().contains("role");
    QueryFilterRole role = filter.role();

    if (roleNegated && role == QueryFilterRole.ACTED) {
      // "not starring X" (a literal example in QueryParsingService's own system prompt, so a
      // real, foreseeable case, not a contrived one): there is no actor-service signal for
      // "movies X is ABSENT from" to build a candidate set from — X's own filmography is exactly
      // the positive (appeared-in) set, not a broader base to subtract from, and no client here
      // can enumerate the complement over the whole catalog. An earlier version of this method
      // fell through to the plain cast-filmography branch below for this case, which is the
      // literal OPPOSITE of what "not starring" means — silently wrong, not just unimplemented.
      // Degrading to empty (with this case named in the log) is the honest, safe answer until
      // this gets a real design, matching this class's stated-gaps philosophy elsewhere.
      log.warn(
          "Negated role=ACTED (\"not starring\") has no resolvable candidate set from this"
              + " service's data — returning empty rather than the wrong (unfiltered) filmography");
      return new LinkedHashMap<>();
    }

    if (roleNegated && role != null) {
      // "movies X didn't direct"/"didn't produce": the base signal is X's broader cast
      // filmography (the most inclusive "X is in this movie" signal available), with the
      // negated role's own credits excluded from it — stated interpretation, see class
      // Javadoc's negation note.
      Map<Long, SearchResultMovieDto> base = toMap(actorCatalogClient.fetchCastCredits(personId));
      Set<Long> excluded = toMovieIds(actorCatalogClient.fetchCrewCredits(personId, role));
      base.keySet().removeAll(excluded);
      return base;
    }

    if (role == QueryFilterRole.DIRECTED || role == QueryFilterRole.PRODUCED) {
      return toMap(actorCatalogClient.fetchCrewCredits(personId, role));
    }

    // role == ACTED or null: cast filmography is the correct/default signal either way.
    return toMap(actorCatalogClient.fetchCastCredits(personId));
  }

  /**
   * @param credits credits to key by movie id
   * @return a mutable, insertion-ordered map from movie id to this endpoint's result shape
   */
  private static Map<Long, SearchResultMovieDto> toMap(List<PersonCredit> credits) {
    Map<Long, SearchResultMovieDto> map = new LinkedHashMap<>();
    for (PersonCredit credit : credits) {
      map.put(credit.movieId(), toSearchResult(credit));
    }
    return map;
  }

  /**
   * @param credits credits to extract movie ids from
   * @return the distinct movie ids referenced
   */
  private static Set<Long> toMovieIds(List<PersonCredit> credits) {
    return credits.stream().map(PersonCredit::movieId).collect(java.util.stream.Collectors.toSet());
  }

  /**
   * @param credit an actor-service credit
   * @return this endpoint's result shape — {@code overview} is empty since credit data doesn't
   *     carry one
   */
  private static SearchResultMovieDto toSearchResult(PersonCredit credit) {
    return new SearchResultMovieDto(
        credit.movieId(),
        credit.title(),
        "",
        credit.releaseDate(),
        credit.posterPath(),
        credit.voteAverage());
  }

  /**
   * @param movie a movie-service list item
   * @return this endpoint's result shape
   */
  private static SearchResultMovieDto toSearchResult(MovieListItem movie) {
    return new SearchResultMovieDto(
        movie.tmdbId(),
        movie.title(),
        movie.overview(),
        movie.releaseDate(),
        movie.posterPath(),
        movie.voteAverage());
  }
}
