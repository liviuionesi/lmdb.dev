package dev.lmdb.ai.dto;

/**
 * How a {@link StructuredQueryFilterDto}'s {@code personName} relates to a movie, per ADR-020's
 * filter shape. Matches the crew/cast distinction actor-service's filmography exposes (#217).
 */
public enum QueryFilterRole {
  /** {@code personName} is credited as a cast member. */
  ACTED,

  /** {@code personName} is credited as director. */
  DIRECTED,

  /** {@code personName} is credited as producer. */
  PRODUCED
}
