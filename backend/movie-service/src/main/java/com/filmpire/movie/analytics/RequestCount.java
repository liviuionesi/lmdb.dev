package com.filmpire.movie.analytics;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document storing the de-duplicated request count for a single TMDB facade resource key.
 *
 * <p>The canonical {@link #key} field (e.g. {@code "movie:634649"} or {@code
 * "category:popular:page:1"}) doubles as the document's primary identifier, ensuring each resource
 * has exactly one counter document. Increments are performed as atomic upserts via {@code
 * $inc}/{@code $setOnInsert}, making replay of the same Kafka offset idempotent — replaying an
 * already-processed offset will find the document and upsert against it without inflating the count
 * (the per-event deduplication is performed at the consumer level via the processed event log, see
 * {@link TmdbAnalyticsConsumer}).
 *
 * @param key Canonical resource routing key — unique index, used as document {@code _id}.
 * @param endpointType Functional category of the facade endpoint (e.g. {@code "MOVIE_DETAIL"}).
 * @param count Accumulated number of save-through events received for this resource.
 * @param lastUpdatedAt Timestamp of the most recent increment.
 */
@Document(collection = "analytics_request_counts")
public record RequestCount(
    @Id String key, @Indexed String endpointType, long count, Instant lastUpdatedAt) {}
