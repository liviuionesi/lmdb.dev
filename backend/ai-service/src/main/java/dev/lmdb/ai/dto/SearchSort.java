package dev.lmdb.ai.dto;

/** The order a search asks for its results in. Without one, results keep their source order. */
public enum SearchSort {
  /** Highest average vote first. */
  RATING,
  /** Highest box-office revenue first. */
  REVENUE,
  /** Latest release date first. */
  NEWEST,
  /** Earliest release date first. */
  OLDEST
}
