package dev.lmdb.ai.service;

import dev.lmdb.ai.client.ActorCatalogClient;
import dev.lmdb.ai.client.AwardsClient;
import dev.lmdb.ai.client.MovieCatalogClient;
import dev.lmdb.ai.client.MovieDetails;
import dev.lmdb.ai.client.MovieListItem;
import dev.lmdb.ai.client.PersonCredit;
import dev.lmdb.ai.dto.NaturalLanguageSearchResponseDto;
import dev.lmdb.ai.dto.OscarCategory;
import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.SearchResultMovieDto;
import dev.lmdb.ai.dto.SearchSort;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runs a natural-language movie query end to end: it reads the query into a filter, looks up each
 * thing the filter names, combines the results, and checks them against the query.
 *
 * <p>Steps, in order:
 *
 * <ol>
 *   <li><b>Parse.</b> {@link QueryParsingService} turns the text into a {@link
 *       StructuredQueryFilterDto}. A query that is only a title is searched as a title.
 *   <li><b>Anchors.</b> A named person, franchise, Oscar category and collaborators each give a set
 *       of movies. A movie must be in every set. actor-service and movie-service fetch from TMDB
 *       when they do not know a name yet, and save what they fetch.
 *   <li><b>Pool.</b> With no anchor, keywords are searched by title, and years and genre are
 *       discovered.
 *   <li><b>Years.</b> Each movie's own release date must fall inside the range.
 *   <li><b>Model check.</b> A genre on an anchored set, keywords, and title-search guesses cannot
 *       be checked by data, so {@link RelevanceFilterService} asks the model which movies fit.
 *   <li><b>Arranging.</b> Movies below a minimum rating are dropped, the rest are sorted, and the
 *       first N are kept.
 *   <li><b>Relaxing.</b> If nothing is left, the weakest criterion is dropped and the search runs
 *       again: keywords, minimum rating, genre, collaborators, then years. The response names what
 *       was dropped. The person and the franchise are never dropped.
 * </ol>
 *
 * <p>Negation is supported for a role only ("didn't direct"). A negated {@code ACTED} role ("not
 * starring X") gives no movies, because no data source can list the movies X is absent from.
 *
 * <p>Collaborators: {@link QueryParsingService}, {@link ActorCatalogClient}, {@link
 * MovieCatalogClient}, {@link RelevanceFilterService}.
 */
@Service
@Slf4j
public class QueryAggregationService {

  /** How many movies a title search asks movie-service for. */
  private static final int TITLE_SEARCH_RESULT_CAP = 200;

  /** How many movies a discover call asks movie-service for. */
  private static final int DISCOVER_RESULT_CAP = 100;

  /** The most movies sent to the model check. Later movies are left out of the result. */
  private static final int MODEL_CHECK_LIMIT = 80;

  /** The most award winners whose details are fetched. */
  private static final int AWARD_DETAILS_CAP = 150;

  /** The most movies whose revenue is fetched for a sort by revenue. */
  private static final int REVENUE_DETAILS_CAP = 60;

  /** The criteria that may be dropped when nothing matches, weakest first. */
  private enum Criterion {
    KEYWORDS("keywords"),
    MIN_RATING("minRating"),
    GENRE("genre"),
    COLLABORATORS("collaborators"),
    YEARS("years");

    private final String label;

    Criterion(String label) {
      this.label = label;
    }

    /**
     * Tells whether a filter uses this criterion.
     *
     * @param filter the filter to check
     * @return {@code true} if the filter has a value for it
     */
    boolean isUsedBy(StructuredQueryFilterDto filter) {
      return switch (this) {
        case KEYWORDS -> !filter.keywords().isEmpty();
        case MIN_RATING -> filter.minRating() != null;
        case GENRE -> filter.genre() != null;
        case COLLABORATORS -> !filter.collaborators().isEmpty();
        case YEARS -> filter.yearFrom() != null || filter.yearTo() != null;
      };
    }

    /**
     * Copies a filter without this criterion.
     *
     * @param filter the filter to copy
     * @return the copy with this criterion cleared and the rest unchanged
     */
    StructuredQueryFilterDto clearedIn(StructuredQueryFilterDto filter) {
      return new StructuredQueryFilterDto(
          filter.personName(),
          filter.role(),
          this == YEARS ? null : filter.yearFrom(),
          this == YEARS ? null : filter.yearTo(),
          this == COLLABORATORS ? List.of() : filter.collaborators(),
          this == GENRE ? null : filter.genre(),
          filter.negated(),
          filter.franchise(),
          this == KEYWORDS ? List.of() : filter.keywords(),
          filter.sortBy(),
          filter.limit(),
          this == MIN_RATING ? null : filter.minRating(),
          filter.award(),
          null);
    }
  }

  private final QueryParsingService queryParsingService;
  private final ActorCatalogClient actorCatalogClient;
  private final MovieCatalogClient movieCatalogClient;
  private final RelevanceFilterService relevanceFilterService;
  private final AwardsClient awardsClient;

  /**
   * The outcome of one search pass.
   *
   * @param results the movies found
   * @param anchorMissing {@code true} when a named person or franchise gave no movies at all, so
   *     dropping other criteria cannot help
   */
  private record Attempt(List<SearchResultMovieDto> results, boolean anchorMissing) {}

  /**
   * The movies from a named person and franchise.
   *
   * @param movies the movies in every named set, or {@code null} if the query names neither
   * @param guessed {@code true} if a title search stood in for a lookup, so the model must check
   */
  private record Anchors(Map<Long, SearchResultMovieDto> movies, boolean guessed) {}

  /**
   * The starting movies when nothing is named.
   *
   * @param movies the movies found
   * @param genreApplied {@code true} if discover already applied the genre
   */
  private record Pool(Map<Long, SearchResultMovieDto> movies, boolean genreApplied) {}

  /**
   * Builds the service.
   *
   * @param queryParsingService reads the query text into a filter
   * @param actorCatalogClient looks up people and their credits
   * @param movieCatalogClient looks up franchises, titles, genres and discovered movies
   * @param relevanceFilterService asks the model which movies fit the query
   * @param awardsClient looks up Academy Award winners
   */
  public QueryAggregationService(
      QueryParsingService queryParsingService,
      ActorCatalogClient actorCatalogClient,
      MovieCatalogClient movieCatalogClient,
      RelevanceFilterService relevanceFilterService,
      AwardsClient awardsClient) {
    this.queryParsingService = queryParsingService;
    this.actorCatalogClient = actorCatalogClient;
    this.movieCatalogClient = movieCatalogClient;
    this.relevanceFilterService = relevanceFilterService;
    this.awardsClient = awardsClient;
  }

  /**
   * Parses and executes one natural-language query.
   *
   * @param rawQuery the query text, typed or transcribed from dictation
   * @return the matching movies, and the criteria that were dropped to find them
   */
  public NaturalLanguageSearchResponseDto search(String rawQuery) {
    StructuredQueryFilterDto filter = queryParsingService.parse(rawQuery);

    if (filter.plainTitle() != null) {
      log.info("Executing plain-title fallback search");
      return new NaturalLanguageSearchResponseDto(
          List.copyOf(titleSearch(filter.plainTitle()).values()));
    }

    log.info("Executing structured filter search: role={}", filter.role());
    StructuredQueryFilterDto current = filter;
    Attempt attempt = execute(rawQuery, current);
    List<String> dropped = new ArrayList<>();

    // Drop one criterion at a time, weakest first, until something is found.
    for (Criterion criterion : Criterion.values()) {
      boolean canRelax =
          attempt.results().isEmpty() && !attempt.anchorMissing() && criterion.isUsedBy(current);
      if (canRelax) {
        current = criterion.clearedIn(current);
        dropped.add(criterion.label);
        log.info("No results, dropping '{}' and searching again", criterion.label);
        attempt = execute(rawQuery, current);
      }
    }

    if (attempt.results().isEmpty()) {
      return new NaturalLanguageSearchResponseDto(List.of());
    }
    return new NaturalLanguageSearchResponseDto(attempt.results(), dropped);
  }

  /**
   * Runs one search pass for a filter: anchors, pool, years, the model check, then rating, sort and
   * count.
   *
   * @param rawQuery the user's text, shown to the model check
   * @param filter the criteria to apply
   * @return the movies found, and whether a named anchor was missing
   */
  private Attempt execute(String rawQuery, StructuredQueryFilterDto filter) {
    // 1. Anchors: a movie must be in every named set.
    Anchors anchors = findAnchors(filter);
    Map<Long, SearchResultMovieDto> candidates = anchors.movies();
    if (candidates != null && candidates.isEmpty()) {
      return new Attempt(List.of(), true);
    }

    if (!filter.collaborators().isEmpty()) {
      candidates = withCollaborators(candidates, filter.collaborators());
      if (candidates.isEmpty()) {
        return new Attempt(List.of(), false);
      }
    }

    // 2. Pool: with no anchor, start from keyword title searches and from discover.
    boolean genreApplied = false;
    if (candidates == null) {
      Pool pool = buildPool(filter);
      candidates = pool.movies();
      genreApplied = pool.genreApplied();
    }

    // 3. Years: the movie's own release date must be inside the range.
    keepReleasedWithin(candidates, filter.yearFrom(), filter.yearTo());

    // 4. Model check, for what data cannot verify.
    List<SearchResultMovieDto> checked =
        modelCheck(rawQuery, filter, candidates, anchors.guessed(), genreApplied);

    // 5. Minimum rating, sort order and count.
    return new Attempt(arrange(checked, filter), false);
  }

  /**
   * Looks up the movies for the named person and franchise.
   *
   * @param filter the criteria
   * @return the movies in every named set, or none if the query names neither
   */
  private Anchors findAnchors(StructuredQueryFilterDto filter) {
    Map<Long, SearchResultMovieDto> movies = null;
    boolean guessed = false;

    if (filter.personName() != null) {
      movies = personMovies(filter);
    }

    if (filter.franchise() != null) {
      Optional<Map<Long, SearchResultMovieDto>> franchise = franchiseMovies(filter.franchise());
      // No collection with that name: series names often appear in their titles.
      guessed = franchise.isEmpty();
      movies = intersect(movies, franchise.orElseGet(() -> titleSearch(filter.franchise())));
    }
    if (filter.award() != null) {
      movies = intersect(movies, awardMovies(filter.award()));
    }
    return new Anchors(movies, guessed);
  }

  /**
   * Gets the movies for the named person. If no such person exists, the name is tried as a
   * franchise, because the model sometimes writes a franchise where a person goes.
   *
   * @param filter the criteria; {@code personName} must not be {@code null}
   * @return the person's movies, or the franchise's, or none
   */
  private Map<Long, SearchResultMovieDto> personMovies(StructuredQueryFilterDto filter) {
    Optional<Long> personId = actorCatalogClient.findPersonId(filter.personName());
    if (personId.isPresent()) {
      return resolvePersonCredits(personId.get(), filter);
    }
    log.info("No person with the name in the query is known; trying the name as a franchise");
    return franchiseMovies(filter.personName()).orElseGet(LinkedHashMap::new);
  }

  /**
   * Keeps only the movies that every collaborator was also in.
   *
   * @param candidates the movies so far, or {@code null} if there are none yet
   * @param collaborators the people who must also be in the movie
   * @return the movies all of them were in; empty if a collaborator is not a known person
   */
  private Map<Long, SearchResultMovieDto> withCollaborators(
      Map<Long, SearchResultMovieDto> candidates, List<String> collaborators) {
    Map<Long, SearchResultMovieDto> result = candidates;
    for (String collaborator : collaborators) {
      Optional<Long> collaboratorId = actorCatalogClient.findPersonId(collaborator);
      if (collaboratorId.isEmpty()) {
        log.info("No person named '{}' is known, so the movie set is empty", collaborator);
        return new LinkedHashMap<>();
      }
      result = intersect(result, toMap(actorCatalogClient.fetchCastCredits(collaboratorId.get())));
    }
    return result;
  }

  /**
   * Builds the starting movies when the query names no person or franchise: keyword title searches,
   * plus discover for years and genre. A query with neither keywords, years nor genre, such as "top
   * 10 highest rated movies", starts from the most popular movies.
   *
   * @param filter the criteria
   * @return the movies, and whether discover applied the genre
   */
  private Pool buildPool(StructuredQueryFilterDto filter) {
    Map<Long, SearchResultMovieDto> movies = new LinkedHashMap<>();
    for (String keyword : filter.keywords()) {
      movies.putAll(titleSearch(keyword));
    }

    boolean hasYears = filter.yearFrom() != null || filter.yearTo() != null;
    boolean nothingElse = filter.keywords().isEmpty();
    if (!hasYears && filter.genre() == null && !nothingElse) {
      return new Pool(movies, false);
    }

    Long genreId =
        filter.genre() == null ? null : movieCatalogClient.findGenreId(filter.genre()).orElse(null);
    for (MovieListItem movie :
        movieCatalogClient.discover(
            filter.yearFrom(), filter.yearTo(), genreId, DISCOVER_RESULT_CAP)) {
      movies.putIfAbsent(movie.tmdbId(), toSearchResult(movie));
    }
    return new Pool(movies, genreId != null);
  }

  /**
   * Removes the movies released outside a year range. Does nothing when there is no range.
   *
   * @param candidates the movies; changed in place
   * @param yearFrom first year, or {@code null} for no lower bound
   * @param yearTo last year, or {@code null} for no upper bound
   */
  private static void keepReleasedWithin(
      Map<Long, SearchResultMovieDto> candidates, Integer yearFrom, Integer yearTo) {
    if (yearFrom != null || yearTo != null) {
      candidates.values().removeIf(movie -> !releasedWithin(movie.releaseDate(), yearFrom, yearTo));
    }
  }

  /**
   * Asks the model which movies fit the query, when data could not check every criterion.
   *
   * @param rawQuery the user's text
   * @param filter the criteria
   * @param candidates the movies that passed the data checks
   * @param guessed {@code true} if a title search stood in for a lookup
   * @param genreApplied {@code true} if discover already applied the genre
   * @return the movies that fit; all of them if no check was needed
   */
  private List<SearchResultMovieDto> modelCheck(
      String rawQuery,
      StructuredQueryFilterDto filter,
      Map<Long, SearchResultMovieDto> candidates,
      boolean guessed,
      boolean genreApplied) {
    List<SearchResultMovieDto> movies = List.copyOf(candidates.values());
    boolean needed =
        guessed || !filter.keywords().isEmpty() || (filter.genre() != null && !genreApplied);
    if (!needed || movies.isEmpty()) {
      return movies;
    }
    List<SearchResultMovieDto> judged =
        movies.size() > MODEL_CHECK_LIMIT ? movies.subList(0, MODEL_CHECK_LIMIT) : movies;
    return relevanceFilterService.filter(rawQuery, judged);
  }

  /**
   * Lists the movies that won an Oscar category, with their details.
   *
   * @param category the category
   * @return the winning movies keyed by id; empty if the award lookup found none
   */
  private Map<Long, SearchResultMovieDto> awardMovies(OscarCategory category) {
    List<Long> ids = awardsClient.findWinningMovieIds(category);
    List<Long> capped = ids.size() > AWARD_DETAILS_CAP ? ids.subList(0, AWARD_DETAILS_CAP) : ids;
    Map<Long, SearchResultMovieDto> movies = new LinkedHashMap<>();
    for (MovieDetails details : movieCatalogClient.fetchMovieDetails(capped)) {
      movies.put(details.tmdbId(), toSearchResult(details));
    }
    return movies;
  }

  /**
   * Applies the minimum rating, the sort order and the count, in that order.
   *
   * @param movies the movies that passed the other checks
   * @param filter the criteria
   * @return the movies to return
   */
  private List<SearchResultMovieDto> arrange(
      List<SearchResultMovieDto> movies, StructuredQueryFilterDto filter) {
    Double minRating = filter.minRating();
    List<SearchResultMovieDto> rated =
        minRating == null
            ? movies
            : movies.stream()
                .filter(movie -> movie.voteAverage() != null && movie.voteAverage() >= minRating)
                .toList();
    List<SearchResultMovieDto> sorted = sorted(rated, filter.sortBy());
    Integer limit = filter.limit();
    return limit == null || limit >= sorted.size() ? sorted : sorted.subList(0, limit);
  }

  /**
   * Sorts movies. A sort by revenue first fetches the revenue, because lists and credits do not
   * carry it.
   *
   * @param movies the movies to sort
   * @param sort the order, or {@code null} to keep the source order
   * @return the sorted movies; movies with no value for the sort go last
   */
  private List<SearchResultMovieDto> sorted(List<SearchResultMovieDto> movies, SearchSort sort) {
    if (sort == null) {
      return movies;
    }
    List<SearchResultMovieDto> source = sort == SearchSort.REVENUE ? withRevenue(movies) : movies;
    return source.stream().sorted(comparatorFor(sort)).toList();
  }

  /**
   * Adds revenue to the first movies of a list. Movies past the cap, or with no known revenue, get
   * none and sort last.
   *
   * @param movies the movies
   * @return the same movies, with revenue where it is known
   */
  private List<SearchResultMovieDto> withRevenue(List<SearchResultMovieDto> movies) {
    List<Long> ids =
        movies.stream().limit(REVENUE_DETAILS_CAP).map(SearchResultMovieDto::movieId).toList();
    Map<Long, Long> revenueById = new HashMap<>();
    for (MovieDetails details : movieCatalogClient.fetchMovieDetails(ids)) {
      if (details.revenue() != null && details.revenue() > 0) {
        revenueById.put(details.tmdbId(), details.revenue());
      }
    }
    return movies.stream()
        .map(
            movie ->
                new SearchResultMovieDto(
                    movie.movieId(),
                    movie.title(),
                    movie.overview(),
                    movie.releaseDate(),
                    movie.posterPath(),
                    movie.voteAverage(),
                    revenueById.get(movie.movieId())))
        .toList();
  }

  /**
   * Builds the comparator for a sort order. A missing value sorts last, whatever the direction.
   *
   * @param sort the order
   * @return the comparator
   */
  private static Comparator<SearchResultMovieDto> comparatorFor(SearchSort sort) {
    return switch (sort) {
      case RATING ->
          Comparator.comparing(
              SearchResultMovieDto::voteAverage, Comparator.nullsLast(Comparator.reverseOrder()));
      case REVENUE ->
          Comparator.comparing(
              SearchResultMovieDto::revenue, Comparator.nullsLast(Comparator.reverseOrder()));
      case NEWEST ->
          Comparator.comparing(
              QueryAggregationService::dateOf, Comparator.nullsLast(Comparator.reverseOrder()));
      case OLDEST ->
          Comparator.comparing(
              QueryAggregationService::dateOf, Comparator.nullsLast(Comparator.naturalOrder()));
    };
  }

  /**
   * Reads a movie's release date for sorting. Dates are {@code yyyy-MM-dd}, so text order is date
   * order.
   *
   * @param movie a movie
   * @return the release date, or {@code null} if it is missing or blank
   */
  private static String dateOf(SearchResultMovieDto movie) {
    String date = movie.releaseDate();
    return date == null || date.isBlank() ? null : date;
  }

  /**
   * Looks up a franchise by name and lists its movies.
   *
   * @param name the franchise name
   * @return the movies keyed by id, or empty if no collection has that name
   */
  private Optional<Map<Long, SearchResultMovieDto>> franchiseMovies(String name) {
    return movieCatalogClient
        .findCollectionId(name)
        .map(
            id -> {
              Map<Long, SearchResultMovieDto> map = new LinkedHashMap<>();
              for (MovieListItem movie : movieCatalogClient.fetchCollectionMovies(id)) {
                map.put(movie.tmdbId(), toSearchResult(movie));
              }
              return map;
            });
  }

  /**
   * Searches movie titles.
   *
   * @param title the text to search for
   * @return the matches keyed by id, in the order movie-service ranks them
   */
  private Map<Long, SearchResultMovieDto> titleSearch(String title) {
    Map<Long, SearchResultMovieDto> map = new LinkedHashMap<>();
    for (MovieListItem movie : movieCatalogClient.searchByTitle(title, TITLE_SEARCH_RESULT_CAP)) {
      map.put(movie.tmdbId(), toSearchResult(movie));
    }
    return map;
  }

  /**
   * Keeps only the movies that are in both sets.
   *
   * @param existing the movies so far, or {@code null} if there is no set yet
   * @param incoming the next set
   * @return {@code incoming} if there was no set yet; otherwise {@code existing} without the movies
   *     that are not in {@code incoming}
   */
  private static Map<Long, SearchResultMovieDto> intersect(
      Map<Long, SearchResultMovieDto> existing, Map<Long, SearchResultMovieDto> incoming) {
    if (existing == null) {
      return incoming;
    }
    existing.keySet().retainAll(incoming.keySet());
    return existing;
  }

  /**
   * Gets the primary person's credits for the role the query asks about. This is the one step that
   * reads {@code role} and {@code negated} together.
   *
   * <ul>
   *   <li>Negated {@code ACTED} ("not starring X"): empty. No source lists the movies X is absent
   *       from, and X's own filmography would be the opposite of what was asked.
   *   <li>Negated {@code DIRECTED} or {@code PRODUCED}: X's cast credits without X's credits in
   *       that role.
   *   <li>{@code DIRECTED} or {@code PRODUCED}: X's crew credits in that role.
   *   <li>Otherwise: X's cast credits.
   * </ul>
   *
   * @param personId the primary person's TMDB id
   * @param filter the filter; only {@code role} and {@code negated} are read
   * @return the credits keyed by movie id, in a stable order
   */
  private Map<Long, SearchResultMovieDto> resolvePersonCredits(
      Long personId, StructuredQueryFilterDto filter) {
    boolean roleNegated = filter.negated().contains("role");
    QueryFilterRole role = filter.role();

    if (roleNegated && role == QueryFilterRole.ACTED) {
      log.warn("Negated role ACTED has no candidate set; returning no movies");
      return new LinkedHashMap<>();
    }

    if (roleNegated && role != null) {
      Map<Long, SearchResultMovieDto> base = toMap(actorCatalogClient.fetchCastCredits(personId));
      Set<Long> excluded = toMovieIds(actorCatalogClient.fetchCrewCredits(personId, role));
      base.keySet().removeAll(excluded);
      return base;
    }

    if (role == QueryFilterRole.DIRECTED || role == QueryFilterRole.PRODUCED) {
      return toMap(actorCatalogClient.fetchCrewCredits(personId, role));
    }

    return toMap(actorCatalogClient.fetchCastCredits(personId));
  }

  /**
   * Tells whether a release date falls inside a year range, both ends inclusive.
   *
   * @param releaseDate a date starting with a four-digit year, such as {@code 1994-06-23}; may be
   *     {@code null} or blank
   * @param yearFrom first year of the range, or {@code null} for no lower bound
   * @param yearTo last year of the range, or {@code null} for no upper bound
   * @return {@code true} if the year is in range; {@code false} if it is outside, or if the date is
   *     missing or has no readable year
   */
  private static boolean releasedWithin(String releaseDate, Integer yearFrom, Integer yearTo) {
    if (releaseDate == null || releaseDate.length() < 4) {
      return false;
    }
    int year;
    try {
      year = Integer.parseInt(releaseDate.substring(0, 4));
    } catch (NumberFormatException e) {
      return false;
    }
    return (yearFrom == null || year >= yearFrom) && (yearTo == null || year <= yearTo);
  }

  /**
   * Keys credits by movie id.
   *
   * @param credits a person's credits
   * @return the credits as results, in order
   */
  private static Map<Long, SearchResultMovieDto> toMap(List<PersonCredit> credits) {
    Map<Long, SearchResultMovieDto> map = new LinkedHashMap<>();
    for (PersonCredit credit : credits) {
      map.put(credit.movieId(), toSearchResult(credit));
    }
    return map;
  }

  /**
   * Collects the movie ids of some credits.
   *
   * @param credits a person's credits
   * @return the movie ids
   */
  private static Set<Long> toMovieIds(List<PersonCredit> credits) {
    return credits.stream().map(PersonCredit::movieId).collect(Collectors.toSet());
  }

  /**
   * Converts a credit into a search result. Credits carry no overview.
   *
   * @param credit the credit
   * @return the result
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
   * Converts a movie-service list item into a search result.
   *
   * @param movie the list item
   * @return the result
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

  /**
   * Converts movie details into a search result.
   *
   * @param details the details
   * @return the result, with revenue where it is known
   */
  private static SearchResultMovieDto toSearchResult(MovieDetails details) {
    Long revenue = details.revenue() != null && details.revenue() > 0 ? details.revenue() : null;
    return new SearchResultMovieDto(
        details.tmdbId(),
        details.title(),
        details.overview(),
        details.releaseDate(),
        details.posterPath(),
        details.voteAverage(),
        revenue);
  }
}
