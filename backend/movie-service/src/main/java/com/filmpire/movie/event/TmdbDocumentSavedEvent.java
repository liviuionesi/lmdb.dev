package com.filmpire.movie.event;

import java.io.Serializable;
import lombok.Builder;

/**
 * Event payload broadcast to Kafka topic {@code tmdb.document.saved} whenever the TMDB facade
 * performs a read-through or save-through operation against upstream TMDB or MongoDB.
 *
 * <p>Per ADR-006 and Task #40, this event encapsulates only minimal request routing metadata and an
 * epoch timestamp rather than full movie documentation payload bytes. This maintains a lightweight
 * event streaming footprint while enabling downstream analytics consumers (#41) to track asset
 * popularity and aggregate most-requested views idempotently.
 *
 * @param eventId Unique identifier assigned to each event emission for consumer idempotency and
 *     offset replay deduplication.
 * @param key Canonical routing key representing the requested resource (e.g., {@code
 *     "movie:634649"} or {@code "category:popular:page:1"}).
 * @param endpointType Categorized API functionality group (e.g., {@code "MOVIE_DETAIL"}, {@code
 *     "MOVIE_CATEGORY"}, {@code "DISCOVER"}).
 * @param path The relative HTTP request path invoked on the TMDB facade (e.g., {@code
 *     "/movie/popular"}).
 * @param timestamp Epoch timestamp in milliseconds indicating precisely when the save-through event
 *     was generated.
 */
@Builder
public record TmdbDocumentSavedEvent(
    String eventId, String key, String endpointType, String path, long timestamp)
    implements Serializable {}
