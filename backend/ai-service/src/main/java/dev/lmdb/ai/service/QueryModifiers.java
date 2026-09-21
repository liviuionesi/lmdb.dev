package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.OscarCategory;
import dev.lmdb.ai.dto.SearchSort;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a query says about time, order, count, rating and awards, read straight from its text. These
 * parts follow fixed phrasing ("the last 20 years", "the 1990s", "top 10", "rated above 7"), so
 * plain patterns read them exactly and a model cannot get them wrong. {@link FilterGrounding} adds
 * the result to the filter the model produced.
 *
 * @param yearFrom first year of a range such as "the last 20 years" or "the 1990s", or {@code null}
 * @param yearTo last year of a relative range such as "this year", or {@code null}
 * @param sortBy the order asked for, or {@code null} for the source order
 * @param limit how many results to keep, or {@code null} for all
 * @param minRating the lowest average vote allowed, or {@code null} for any
 * @param award the Oscar category whose winners are wanted, or {@code null}
 */
record QueryModifiers(
    Integer yearFrom,
    Integer yearTo,
    SearchSort sortBy,
    Integer limit,
    Double minRating,
    OscarCategory award) {

  private static final Pattern LAST_N_YEARS =
      Pattern.compile("\\b(?:last|past|previous)\\s+(\\d{1,2})\\s+years?\\b");
  private static final Pattern LAST_DECADE = Pattern.compile("\\b(?:last|past)\\s+decade\\b");
  private static final Pattern FOUR_DIGIT_DECADE = Pattern.compile("\\b(\\d{3})0'?s\\b");
  private static final Pattern TWO_DIGIT_DECADE = Pattern.compile("\\b(\\d)0'?s\\b");
  private static final Pattern THIS_YEAR = Pattern.compile("\\bthis\\s+year\\b");
  private static final Pattern LAST_YEAR = Pattern.compile("\\blast\\s+year\\b");

  private static final Pattern REVENUE =
      Pattern.compile("revenue|box[- ]office|grossing|grossed|earning|earned");
  private static final Pattern NEWEST =
      Pattern.compile("newest|latest|most recent|release date|recent first|new to old");
  private static final Pattern OLDEST = Pattern.compile("oldest|earliest|old to new|chronolog");
  private static final Pattern RATING =
      Pattern.compile(
          "\\brating\\b|\\brated\\b|highest|\\bscores?\\b|acclaimed|\\btop\\b|"
              + "\\bbest\\b(?!\\s+(?:actor|actress|director|picture|supporting))");

  private static final Pattern LIMIT_AFTER =
      Pattern.compile("\\b(?:top|first|best|highest|greatest)(?:[- ]rated)?\\s+(\\d{1,3})\\b");
  private static final Pattern LIMIT_BEFORE =
      Pattern.compile("\\b(\\d{1,3})\\s+(?:best|highest|top|greatest|most)\\b");

  private static final Pattern MIN_RATING_AFTER_WORD =
      Pattern.compile(
          "\\b(?:rating|rated|score)\\b[^\\d]{0,20}?"
              + "(?:above|over|at least|higher than|more than|greater than|>=|>)\\s*"
              + "(\\d+(?:\\.\\d+)?)");
  private static final Pattern MIN_RATING_BEFORE_WORD =
      Pattern.compile(
          "\\b(?:above|over|at least|higher than|more than)\\s+(\\d+(?:\\.\\d+)?)\\s+"
              + "(?:stars?|rating|points)\\b");

  private static final Pattern OSCAR = Pattern.compile("\\boscars?\\b|academy awards?");
  private static final Pattern WON = Pattern.compile("\\bwon\\b|\\bwinners?\\b|\\bwinning\\b");

  /**
   * Reads the modifiers from a query.
   *
   * @param text the query, in lower case
   * @param today today's date, used for "the last N years" and "this year"
   * @return the modifiers found; fields the query does not mention are {@code null}
   */
  static QueryModifiers extract(String text, LocalDate today) {
    Integer[] years = relativeYears(text, today);
    return new QueryModifiers(
        years[0], years[1], sortOf(text), limitOf(text), minRatingOf(text), awardOf(text));
  }

  /**
   * Reads a relative year range.
   *
   * @param text the lower-case query
   * @param today today's date
   * @return a two-element array of first and last year; entries the query does not fix are {@code
   *     null}
   */
  private static Integer[] relativeYears(String text, LocalDate today) {
    int year = today.getYear();
    Matcher lastN = LAST_N_YEARS.matcher(text);
    if (lastN.find()) {
      return new Integer[] {year - Integer.parseInt(lastN.group(1)), null};
    }
    if (LAST_DECADE.matcher(text).find()) {
      return new Integer[] {year - 10, null};
    }
    Integer[] decade = decadeIn(text);
    if (decade.length > 0) {
      return decade;
    }
    if (THIS_YEAR.matcher(text).find()) {
      return new Integer[] {year, year};
    }
    if (LAST_YEAR.matcher(text).find()) {
      return new Integer[] {year - 1, year - 1};
    }
    return new Integer[] {null, null};
  }

  /**
   * Reads a decade such as "the 2010s", "the 1990s" or "the 80s". A two-digit decade from 30 to 90
   * is the 1900s; from 00 to 20 it is the 2000s.
   *
   * @param text the lower-case query
   * @return the first and last year of the decade, or an empty array if the query names none
   */
  private static Integer[] decadeIn(String text) {
    Matcher full = FOUR_DIGIT_DECADE.matcher(text);
    if (full.find()) {
      int first = Integer.parseInt(full.group(1)) * 10;
      return new Integer[] {first, first + 9};
    }
    Matcher shortForm = TWO_DIGIT_DECADE.matcher(text);
    if (shortForm.find()) {
      int tens = Integer.parseInt(shortForm.group(1)) * 10;
      int first = tens >= 30 ? 1900 + tens : 2000 + tens;
      return new Integer[] {first, first + 9};
    }
    return new Integer[0];
  }

  /**
   * Reads the sort order. Revenue and date words win over rating words, because "best" is often
   * just a word ("best action movies sorted by revenue").
   *
   * @param text the lower-case query
   * @return the order, or {@code null}
   */
  private static SearchSort sortOf(String text) {
    if (REVENUE.matcher(text).find()) {
      return SearchSort.REVENUE;
    }

    boolean hasDesc = text.contains("descend") || text.matches(".*\\bdesc\\b.*");
    boolean hasAsc = text.contains("ascend") || text.matches(".*\\basc\\b.*");

    if (hasDesc && text.contains("chronolog")) {
      return SearchSort.NEWEST;
    }
    if (hasAsc && text.contains("chronolog")) {
      return SearchSort.OLDEST;
    }
    if (hasDesc && text.contains("release")) {
      return SearchSort.NEWEST;
    }
    if (hasAsc && text.contains("release")) {
      return SearchSort.OLDEST;
    }

    if (OLDEST.matcher(text).find()) {
      return SearchSort.OLDEST;
    }
    if (NEWEST.matcher(text).find()) {
      return SearchSort.NEWEST;
    }
    return RATING.matcher(text).find() ? SearchSort.RATING : null;
  }

  /**
   * Reads "top N" or "N best".
   *
   * @param text the lower-case query
   * @return the count, or {@code null}
   */
  private static Integer limitOf(String text) {
    Matcher after = LIMIT_AFTER.matcher(text);
    if (after.find()) {
      return Integer.parseInt(after.group(1));
    }
    Matcher before = LIMIT_BEFORE.matcher(text);
    return before.find() ? Integer.parseInt(before.group(1)) : null;
  }

  /**
   * Reads a rating threshold such as "rated above 7".
   *
   * @param text the lower-case query
   * @return the threshold, or {@code null}
   */
  private static Double minRatingOf(String text) {
    Matcher afterWord = MIN_RATING_AFTER_WORD.matcher(text);
    if (afterWord.find()) {
      return Double.parseDouble(afterWord.group(1));
    }
    Matcher beforeWord = MIN_RATING_BEFORE_WORD.matcher(text);
    return beforeWord.find() ? Double.parseDouble(beforeWord.group(1)) : null;
  }

  /**
   * Reads an Oscar category. The query needs an Oscar word, or a "won"/"winner" word next to a
   * category; "best actor" alone could mean anything.
   *
   * @param text the lower-case query
   * @return the category, or {@code null}
   */
  private static OscarCategory awardOf(String text) {
    boolean oscar = OSCAR.matcher(text).find();
    boolean won = WON.matcher(text).find();
    OscarCategory category = categoryIn(text);
    if (category != null) {
      return oscar || won ? category : null;
    }
    return oscar ? OscarCategory.BEST_PICTURE : null;
  }

  /**
   * Finds a category name in the text, longest names first.
   *
   * @param text the lower-case query
   * @return the category, or {@code null}
   */
  private static OscarCategory categoryIn(String text) {
    if (text.contains("best supporting actress")) {
      return OscarCategory.BEST_SUPPORTING_ACTRESS;
    }
    if (text.contains("best supporting actor")) {
      return OscarCategory.BEST_SUPPORTING_ACTOR;
    }
    if (text.contains("best actress")) {
      return OscarCategory.BEST_ACTRESS;
    }
    if (text.contains("best actor")) {
      return OscarCategory.BEST_ACTOR;
    }
    if (text.contains("best director")) {
      return OscarCategory.BEST_DIRECTOR;
    }
    boolean picture = text.contains("best picture") || text.contains("best film");
    return picture ? OscarCategory.BEST_PICTURE : null;
  }
}
