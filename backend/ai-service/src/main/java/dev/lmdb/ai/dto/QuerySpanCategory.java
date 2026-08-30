package dev.lmdb.ai.dto;

/**
 * Category of a {@link QuerySpanDto} within a natural-language query's token/span breakdown (#207,
 * ADR-020). Lets the frontend differentiate a highlighted span by something other than color alone
 * — Story #199's own accessibility requirement (color paired with an underline style or
 * icon/tooltip per category, per #199's Notes) depends on each span carrying one of these, not just
 * a display color.
 */
public enum QuerySpanCategory {
  /** A boolean connector word joining two constraints (e.g. "and", "or"). */
  CONNECTOR,

  /**
   * A negation cue together with the verb/phrase it negates (e.g. "didn't direct", "not starring").
   */
  NEGATION,

  /** A recognized entity/value the query resolves to — person, year, genre, or collaborator. */
  ENTITY
}
