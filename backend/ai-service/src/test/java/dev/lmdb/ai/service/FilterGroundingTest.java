package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.ai.dto.StructuredQueryFilterDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FilterGrounding}, which drops every value in a parsed filter that the query text
 * does not support. The filters below are shaped like real llama3.2 replies, including the values
 * it invented.
 */
@DisplayName("FilterGrounding (dropping what the query does not say)")
class FilterGroundingTest {

  // ------------------------------------------------------------------------------------ people

  /** The model named a person the query never mentions. */
  @Test
  @DisplayName("drops a person who is not in the query")
  void dropsAnInventedPerson() {
    StructuredQueryFilterDto parsed =
        filter("Daniel Craig", QueryFilterRole.ACTED, 2000, 2015, List.of("Olga Kurylenko"), null);

    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            parsed, "list me the movies from the James Bond franchise after year 2000");

    assertThat(grounded.personName()).isNull();
    assertThat(grounded.collaborators()).isEmpty();
  }

  /** A person named in full stays. */
  @Test
  @DisplayName("keeps a person named in the query")
  void keepsANamedPerson() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter("Tom Hanks", QueryFilterRole.ACTED, null, null, List.of(), null),
            "Tom Hanks movies");

    assertThat(grounded.personName()).isEqualTo("Tom Hanks");
  }

  /** The user may type only a surname; the model's full name is then still about that person. */
  @Test
  @DisplayName("keeps a full name when the query has the surname")
  void keepsAFullNameWhenTheQueryHasTheSurname() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter("Christopher Nolan", QueryFilterRole.DIRECTED, null, null, List.of(), null),
            "movies directed by Nolan");

    assertThat(grounded.personName()).isEqualTo("Christopher Nolan");
  }

  /** Real collaborators stay; invented ones go. */
  @Test
  @DisplayName("keeps only collaborators named in the query")
  void keepsOnlyNamedCollaborators() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(
                "Brad Pitt",
                QueryFilterRole.ACTED,
                null,
                null,
                List.of("Edward Norton", "Leonardo DiCaprio"),
                null),
            "movies with Brad Pitt and Edward Norton");

    assertThat(grounded.collaborators()).containsExactly("Edward Norton");
  }

  // ------------------------------------------------------------------- franchise and keywords

  /** Every word of the franchise must be in the query. */
  @Test
  @DisplayName("keeps a franchise named in the query and drops one that is not")
  void groundsTheFranchise() {
    StructuredQueryFilterDto withBond = franchiseFilter("James Bond", List.of());

    assertThat(FilterGrounding.ground(withBond, "James Bond movies").franchise())
        .isEqualTo("James Bond");
    assertThat(FilterGrounding.ground(withBond, "spy movies after 2000").franchise()).isNull();
  }

  /**
   * The model repeated the person and the word "movies" as keywords. Only real extra words stay.
   */
  @Test
  @DisplayName("drops keywords that repeat the person or only say movie")
  void dropsRedundantKeywords() {
    StructuredQueryFilterDto parsed =
        new StructuredQueryFilterDto(
            "Brad Pitt",
            QueryFilterRole.ACTED,
            null,
            null,
            List.of(),
            null,
            List.of(),
            null,
            List.of("heist", "movies", "Brad Pitt"),
            null);

    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(parsed, "heist movies with Brad Pitt");

    assertThat(grounded.keywords()).containsExactly("heist");
  }

  /** A keyword that is not in the query is invented. */
  @Test
  @DisplayName("drops a keyword that is not in the query")
  void dropsAnInventedKeyword() {
    StructuredQueryFilterDto parsed =
        new StructuredQueryFilterDto(
            null, null, null, null, List.of(), null, List.of(), null, List.of("robbery"), null);

    assertThat(FilterGrounding.ground(parsed, "heist movies").keywords()).isEmpty();
  }

  // -------------------------------------------------------------------------------------- years

  /** "After year 2000" supports a start of 2000 but not an end of 2015. */
  @Test
  @DisplayName("keeps a year the query states and drops one it does not")
  void groundsYears() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(null, null, 2000, 2015, List.of(), null), "movies after year 2000");

    assertThat(grounded.yearFrom()).isEqualTo(2000);
    assertThat(grounded.yearTo()).isNull();
  }

  /** "In the 1990s" supports 1990 and 1999. */
  @Test
  @DisplayName("accepts a four-digit decade")
  void acceptsAFourDigitDecade() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(null, null, 1990, 1999, List.of(), null), "movies in the 1990s");

    assertThat(grounded.yearFrom()).isEqualTo(1990);
    assertThat(grounded.yearTo()).isEqualTo(1999);
  }

  /** "In the 90s" is a decade too. */
  @Test
  @DisplayName("accepts a two-digit decade")
  void acceptsATwoDigitDecade() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(null, null, 1990, 1999, List.of(), null), "comedies from the 90s");

    assertThat(grounded.yearFrom()).isEqualTo(1990);
    assertThat(grounded.yearTo()).isEqualTo(1999);
  }

  /** Both ends of a written range are supported. */
  @Test
  @DisplayName("accepts both ends of a written range")
  void acceptsAWrittenRange() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(null, null, 2015, 2020, List.of(), null), "action movies from 2015 to 2020");

    assertThat(grounded.yearFrom()).isEqualTo(2015);
    assertThat(grounded.yearTo()).isEqualTo(2020);
  }

  /** "Before 2010" may come back as 2009 or 2010; both are the user's meaning. */
  @Test
  @DisplayName("allows a year one off the stated year, for before and after")
  void allowsAYearOneOff() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter(null, null, null, 2009, List.of(), null), "movies before 2010");

    assertThat(grounded.yearTo()).isEqualTo(2009);
  }

  // --------------------------------------------------------------------------- role and negation

  /** The model said DIRECTED for "Daniel Craig as James Bond". Nothing in the query says that. */
  @Test
  @DisplayName("drops a directing role the query does not support")
  void dropsAnUnsupportedDirectedRole() {
    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(
            filter("Daniel Craig", QueryFilterRole.DIRECTED, null, null, List.of(), null),
            "movies with Daniel Craig as James Bond");

    assertThat(grounded.role()).isNull();
  }

  /** "Directed by" and a plain "by" both point at the director. */
  @Test
  @DisplayName("keeps a directing role when the query says directed or by")
  void keepsASupportedDirectedRole() {
    StructuredQueryFilterDto parsed =
        filter("Christopher Nolan", QueryFilterRole.DIRECTED, null, null, List.of(), null);

    assertThat(FilterGrounding.ground(parsed, "movies directed by Christopher Nolan").role())
        .isEqualTo(QueryFilterRole.DIRECTED);
    assertThat(FilterGrounding.ground(parsed, "movies by Christopher Nolan").role())
        .isEqualTo(QueryFilterRole.DIRECTED);
  }

  /** A producing role needs the word produce in some form. */
  @Test
  @DisplayName("keeps a producing role only when the query says produce")
  void groundsTheProducedRole() {
    StructuredQueryFilterDto parsed =
        filter("Kevin Feige", QueryFilterRole.PRODUCED, null, null, List.of(), null);

    assertThat(FilterGrounding.ground(parsed, "movies produced by Kevin Feige").role())
        .isEqualTo(QueryFilterRole.PRODUCED);
    assertThat(FilterGrounding.ground(parsed, "Kevin Feige movies").role()).isNull();
  }

  /** The model listed "role" as negated for a query with no negation. */
  @Test
  @DisplayName("drops a negation the query does not contain")
  void dropsAnInventedNegation() {
    StructuredQueryFilterDto parsed =
        new StructuredQueryFilterDto(
            "Daniel Craig",
            QueryFilterRole.ACTED,
            null,
            null,
            List.of(),
            null,
            List.of("role"),
            null,
            List.of(),
            null);

    assertThat(FilterGrounding.ground(parsed, "movies with Daniel Craig").negated()).isEmpty();
  }

  /** A real negation stays. */
  @Test
  @DisplayName("keeps a negation the query contains")
  void keepsARealNegation() {
    StructuredQueryFilterDto parsed =
        new StructuredQueryFilterDto(
            "Quentin Tarantino",
            QueryFilterRole.DIRECTED,
            null,
            null,
            List.of(),
            null,
            List.of("role"),
            null,
            List.of(),
            null);

    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(parsed, "films Quentin Tarantino didn't direct");

    assertThat(grounded.negated()).containsExactly("role");
    assertThat(grounded.role()).isEqualTo(QueryFilterRole.DIRECTED);
  }

  /**
   * A role and a negation describe the person. If the person was invented and dropped, keeping the
   * role would search "everything anyone acted in".
   */
  @Test
  @DisplayName("drops the role and the negation together with an invented person")
  void dropsTheRoleAndNegationWithTheirPerson() {
    StructuredQueryFilterDto parsed =
        new StructuredQueryFilterDto(
            "Daniel Craig",
            QueryFilterRole.DIRECTED,
            null,
            null,
            List.of(),
            null,
            List.of("role"),
            null,
            List.of(),
            null);

    StructuredQueryFilterDto grounded =
        FilterGrounding.ground(parsed, "movies not directed by somebody");

    assertThat(grounded.personName()).isNull();
    assertThat(grounded.role()).isNull();
    assertThat(grounded.negated()).isEmpty();
  }

  // ------------------------------------------------------------------------------------- genre

  /** A genre must show up in the query, in the singular or plural. */
  @Test
  @DisplayName("keeps a genre the query mentions, in any form, and drops one it does not")
  void groundsTheGenre() {
    StructuredQueryFilterDto comedy = filter(null, null, null, null, List.of(), "Comedy");
    StructuredQueryFilterDto drama = filter(null, null, null, null, List.of(), "Drama");

    assertThat(FilterGrounding.ground(comedy, "Tom Hanks comedies").genre()).isEqualTo("Comedy");
    assertThat(FilterGrounding.ground(drama, "Tom Hanks comedies").genre()).isNull();
  }

  // ---------------------------------------------------------------------------------- helpers

  /**
   * Builds a filter with the fields these tests care about.
   *
   * @param person person name, or null
   * @param role role, or null
   * @param yearFrom first year, or null
   * @param yearTo last year, or null
   * @param collaborators collaborators
   * @param genre genre, or null
   * @return the filter
   */
  private static StructuredQueryFilterDto filter(
      String person,
      QueryFilterRole role,
      Integer yearFrom,
      Integer yearTo,
      List<String> collaborators,
      String genre) {
    return new StructuredQueryFilterDto(
        person, role, yearFrom, yearTo, collaborators, genre, List.of(), null);
  }

  /**
   * Builds a filter that only names a franchise.
   *
   * @param franchise the franchise name
   * @param keywords keywords
   * @return the filter
   */
  private static StructuredQueryFilterDto franchiseFilter(String franchise, List<String> keywords) {
    return new StructuredQueryFilterDto(
        null, null, null, null, List.of(), null, List.of(), franchise, keywords, null);
  }
}
