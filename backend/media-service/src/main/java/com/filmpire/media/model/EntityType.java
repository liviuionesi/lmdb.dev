package com.filmpire.media.model;

/**
 * Enumeration representing the domain entities that user-uploaded media assets can be bound to
 * within the Filmpire microservices architecture.
 *
 * <p>Supports standard core actors (USER, MOVIE, ACTOR) as well as interactive user-generated
 * platform features (MOVIE_REVIEW) per ARCHITECTURE.md §3.8 and #115.
 */
public enum EntityType {
  /** User account profile asset (e.g. custom avatar image). */
  USER,

  /** Movie catalog asset (user-uploaded supplemental item). */
  MOVIE,

  /** Actor profile asset (user-uploaded supplemental item). */
  ACTOR,

  /** User-generated review attachment asset (screenshot proof, reaction clip). */
  MOVIE_REVIEW
}
