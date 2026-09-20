package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drops every value in a parsed filter that the query text does not support. A small model often
 * invents people, years, roles and negations, and a filter built on an invented value returns the
 * wrong movies or none. Each check is a plain text test against the query, so it works the same for
 * any model.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>A person or collaborator stays if the last word of the name is in the query. A user may
 *       type only a surname. A collaborator that only repeats the franchise name is dropped.
 *   <li>A franchise or keyword stays if all its words are in the query. Keywords that only repeat
 *       the person, the franchise or a word like "movies" are dropped.
 *   <li>A year stays if the query has that year, a year next to it (for "before 2010"), or a decade
 *       that contains it ("1990s", "90s").
 *   <li>A directing role needs "direct" or "by" in the query; a producing role needs "produc".
 *   <li>A negation needs a negating word such as "not", "didn't" or "without".
 *   <li>A role and a negation belong to the person, so they go when the person goes.
 *   <li>Relative years ("the last 20 years"), sort order, "top N", a minimum rating and an Oscar
 *       category are read from the text by {@link QueryModifiers}. Whatever the model put in those
 *       fields is ignored. When the query names an Oscar category, a person or keyword made only of
 *       award words ("best actor") is dropped.
 *   <li>A genre stays if the query has a word that starts like it ("comedies" for Comedy).
 * </ul>
 *
 * <p>Used by {@link QueryParsingService} right after the model answers.
 */
final class FilterGrounding {

  /** Words that name no search topic. */
  private static final Set<String> GENERIC_WORDS =
      Set.of("movie", "movies", "film", "films", "show", "shows", "list");

  /** Words that only describe an award, so they are not a person or a topic. */
  private static final Set<String> AWARD_WORDS =
      Set.of(
          "oscar",
          "oscars",
          "academy",
          "award",
          "awards",
          "best",
          "actor",
          "actress",
          "supporting",
          "director",
          "picture",
          "film",
          "movie",
          "movies",
          "films",
          "winner",
          "winners",
          "won",
          "winning",
          "the",
          "for",
          "all",
          "that");

  private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("\\b(\\d{4})\\b");
  private static final Pattern FOUR_DIGIT_DECADE = Pattern.compile("\\b(\\d{3})0s\\b");
  private static final Pattern TWO_DIGIT_DECADE = Pattern.compile("\\b(\\d)0s\\b");
  private static final Pattern DIRECTING = Pattern.compile("direct|\\bby\\b|filmmaker");
  private static final Pattern PRODUCING = Pattern.compile("produc");
  private static final Pattern NEGATION =
      Pattern.compile("n't|\\bnot\\b|\\bwithout\\b|\\bexcept\\b|\\bnever\\b|\\bno\\b");
  private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

  private FilterGrounding() {}

  /**
   * Returns the filter with every unsupported value removed, using today's date.
   *
   * @param filter what the model returned
   * @param query the text the user typed or said
   * @return a copy that keeps only values the query supports
   */
  static StructuredQueryFilterDto ground(StructuredQueryFilterDto filter, String query) {
    return ground(filter, query, LocalDate.now());
  }

  /**
   * Returns the filter with every unsupported value removed.
   *
   * @param filter what the model returned
   * @param query the text the user typed or said
   * @param today today's date, used for "the last 20 years" and "this year"
   * @return a copy that keeps only values the query supports; {@code plainTitle} is unchanged
   */
  static StructuredQueryFilterDto ground(
      StructuredQueryFilterDto filter, String query, LocalDate today) {
    String text = query.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
    Set<String> words = wordsOf(text);
    QueryModifiers modifiers = QueryModifiers.extract(text, today);
    boolean awardQuery = modifiers.award() != null;

    String person = supportedName(filter.personName(), words);
    if (awardQuery && isOnlyAwardWords(person)) {
      person = null;
    }
    String franchise = supportedPhrase(filter.franchise(), words);
    Set<String> franchiseWords = new HashSet<>(tokensOf(franchise));
    List<String> collaborators =
        filter.collaborators().stream()
            .filter(name -> supportedName(name, words) != null)
            .filter(name -> !franchiseWords.containsAll(tokensOf(name)))
            .toList();

    Set<String> named = new HashSet<>(tokensOf(person));
    named.addAll(franchiseWords);
    collaborators.forEach(name -> named.addAll(tokensOf(name)));
    if (awardQuery) {
      named.addAll(AWARD_WORDS);
    }
    List<String> keywords =
        filter.keywords().stream()
            .filter(keyword -> supportedPhrase(keyword, words) != null)
            .filter(keyword -> !isRedundant(keyword, named))
            .toList();

    // A relative range ("the last 20 years") replaces whatever years the model gave.
    boolean relative = modifiers.yearFrom() != null;
    Integer yearFrom = relative ? modifiers.yearFrom() : supportedYear(filter.yearFrom(), text);
    Integer yearTo = relative ? modifiers.yearTo() : supportedYear(filter.yearTo(), text);

    return new StructuredQueryFilterDto(
        person,
        person == null ? null : supportedRole(filter.role(), text),
        yearFrom,
        yearTo,
        collaborators,
        supportedGenre(filter.genre(), words),
        person != null && NEGATION.matcher(text).find() ? filter.negated() : List.of(),
        franchise,
        keywords,
        modifiers.sortBy(),
        modifiers.limit(),
        modifiers.minRating(),
        modifiers.award(),
        filter.plainTitle());
  }

  /**
   * Tells whether a name is made only of award words, such as "Best Actor".
   *
   * @param name a person's name, or {@code null}
   * @return {@code true} if the name has words and all of them are award words
   */
  private static boolean isOnlyAwardWords(String name) {
    List<String> tokens = tokensOf(name);
    return !tokens.isEmpty() && AWARD_WORDS.containsAll(tokens);
  }

  /**
   * Splits text into lower-case words.
   *
   * @param text any text, or {@code null}
   * @return the words; empty for {@code null} or blank text
   */
  private static List<String> tokensOf(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return List.of(NON_WORD.split(text.toLowerCase(Locale.ROOT).trim())).stream()
        .filter(token -> !token.isEmpty())
        .toList();
  }

  /**
   * Collects the words of the query.
   *
   * @param text the lower-case query
   * @return its words
   */
  private static Set<String> wordsOf(String text) {
    return new HashSet<>(tokensOf(text));
  }

  /**
   * Keeps a person's name if the last word of it is in the query.
   *
   * @param name a person's name, or {@code null}
   * @param words the words of the query
   * @return the name, or {@code null} if it is missing or not supported
   */
  private static String supportedName(String name, Set<String> words) {
    List<String> tokens = tokensOf(name);
    return !tokens.isEmpty() && words.contains(tokens.get(tokens.size() - 1)) ? name : null;
  }

  /**
   * Keeps a phrase if every word of it is in the query.
   *
   * @param phrase a franchise or keyword, or {@code null}
   * @param words the words of the query
   * @return the phrase, or {@code null} if it is missing or not supported
   */
  private static String supportedPhrase(String phrase, Set<String> words) {
    List<String> tokens = tokensOf(phrase);
    return !tokens.isEmpty() && words.containsAll(tokens) ? phrase : null;
  }

  /**
   * Tells whether a keyword adds nothing: it is a generic word, or it repeats a named person or
   * franchise.
   *
   * @param keyword the keyword
   * @param named the words of the person, collaborators and franchise
   * @return {@code true} if the keyword should be dropped
   */
  private static boolean isRedundant(String keyword, Set<String> named) {
    List<String> tokens = tokensOf(keyword);
    return GENERIC_WORDS.containsAll(tokens) || named.containsAll(tokens);
  }

  /**
   * Keeps a year if the query states it.
   *
   * @param year a year from the model, or {@code null}
   * @param text the lower-case query
   * @return the year, or {@code null} if it is missing or not supported
   */
  private static Integer supportedYear(Integer year, String text) {
    if (year == null) {
      return null;
    }
    Set<Integer> allowed = new HashSet<>();
    Matcher years = FOUR_DIGIT_YEAR.matcher(text);
    while (years.find()) {
      int stated = Integer.parseInt(years.group(1));
      allowed.addAll(List.of(stated - 1, stated, stated + 1));
    }
    Matcher decades = FOUR_DIGIT_DECADE.matcher(text);
    while (decades.find()) {
      addDecade(allowed, Integer.parseInt(decades.group(1)) * 10);
    }
    Matcher shortDecades = TWO_DIGIT_DECADE.matcher(text);
    while (shortDecades.find()) {
      int tens = Integer.parseInt(shortDecades.group(1)) * 10;
      addDecade(allowed, 1900 + tens);
      addDecade(allowed, 2000 + tens);
    }
    return allowed.contains(year) ? year : null;
  }

  /**
   * Adds the first and last year of a decade.
   *
   * @param allowed the years allowed so far
   * @param firstYear the first year of the decade, such as 1990
   */
  private static void addDecade(Set<Integer> allowed, int firstYear) {
    allowed.add(firstYear);
    allowed.add(firstYear + 9);
  }

  /**
   * Keeps a directing or producing role only if the query points at it.
   *
   * @param role the role from the model, or {@code null}
   * @param text the lower-case query
   * @return the role, or {@code null} if it is missing or not supported. Acting needs no cue,
   *     because it is also the default.
   */
  private static QueryFilterRole supportedRole(QueryFilterRole role, String text) {
    if (role == QueryFilterRole.DIRECTED) {
      return DIRECTING.matcher(text).find() ? role : null;
    }
    if (role == QueryFilterRole.PRODUCED) {
      return PRODUCING.matcher(text).find() ? role : null;
    }
    return role;
  }

  /**
   * Keeps a genre if a word in the query starts like it.
   *
   * @param genre a genre from the model, or {@code null}
   * @param words the words of the query
   * @return the genre, or {@code null} if it is missing or not supported
   */
  private static String supportedGenre(String genre, Set<String> words) {
    List<String> tokens = tokensOf(genre);
    if (tokens.isEmpty()) {
      return null;
    }
    boolean supported =
        tokens.stream()
            .allMatch(
                token -> {
                  String start = token.substring(0, Math.min(4, token.length()));
                  return words.stream().anyMatch(word -> word.startsWith(start));
                });
    return supported ? genre : null;
  }
}
