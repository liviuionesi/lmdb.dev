package com.filmpire.movie.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmpire.movie.event.TmdbDocumentSavedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that aggregates {@code tmdb.document.saved} events into per-resource request
 * counts stored in MongoDB.
 *
 * <h3>Idempotency guarantee</h3>
 *
 * <p>Each incoming event carries a unique {@link TmdbDocumentSavedEvent#eventId()}. Before
 * processing, the consumer checks a {@code processed_events} set stored on the count document
 * itself. If the event ID is already present, the message is acknowledged and skipped — making
 * Kafka offset replay (e.g. after a consumer restart) safe and non-inflating.
 *
 * <h3>Failure handling</h3>
 *
 * <p>Malformed JSON payloads are logged at WARN level and skipped; they do not cause consumer lag
 * or partition stalls. All other exceptions bubble up so Spring Kafka's default error handler can
 * apply its back-off and retry policy.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TmdbAnalyticsConsumer {

  private static final String TOPIC = "tmdb.document.saved";
  private static final String GROUP_ID = "movie-service-analytics";

  private static final String FIELD_ID = "_id";
  private static final String FIELD_COUNT = "count";
  private static final String FIELD_ENDPOINT_TYPE = "endpointType";
  private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";
  private static final String FIELD_KEY = "key";
  private static final String FIELD_PROCESSED_EVENT_IDS = "processedEventIds";

  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;

  /**
   * Handles a single {@code tmdb.document.saved} Kafka record by deserializing the JSON payload and
   * atomically upserting the request count for the event's canonical resource key in MongoDB.
   *
   * <p>The upsert uses {@code $inc} on the {@code count} field and {@code $addToSet} on {@code
   * processedEventIds} — ensuring that replaying the same Kafka offset after a consumer restart
   * does not inflate the counter (the event ID is already in the set; MongoDB ignores duplicate
   * {@code $addToSet} values, so the count stays correct).
   *
   * @param payload raw JSON string value of the Kafka record
   * @param offset the Kafka partition offset of the received record (for debug logging)
   */
  @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
  public void onDocumentSaved(@Payload String payload, @Header(KafkaHeaders.OFFSET) long offset) {
    try {
      TmdbDocumentSavedEvent event = objectMapper.readValue(payload, TmdbDocumentSavedEvent.class);

      log.debug(
          "Received tmdb.document.saved event id={} key={} offset={}",
          event.eventId(),
          event.key(),
          offset);

      // Idempotency check: skip processing if this eventId was already processed for this key
      Query existsQuery =
          Query.query(
              Criteria.where(FIELD_ID)
                  .is(event.key())
                  .and(FIELD_PROCESSED_EVENT_IDS)
                  .is(event.eventId()));
      if (mongoTemplate.exists(existsQuery, RequestCount.class)) {
        log.debug(
            "Event eventId={} for key={} was already processed; skipping replay",
            event.eventId(),
            event.key());
        return;
      }

      // Idempotent atomic upsert: increment count + track processed event ID
      Query query = Query.query(Criteria.where(FIELD_ID).is(event.key()));
      Update update =
          new Update()
              .inc(FIELD_COUNT, 1L)
              .set(FIELD_ENDPOINT_TYPE, event.endpointType())
              .set(FIELD_LAST_UPDATED_AT, Instant.now())
              .setOnInsert(FIELD_KEY, event.key())
              .addToSet(FIELD_PROCESSED_EVENT_IDS, event.eventId());

      mongoTemplate.findAndModify(
          query,
          update,
          FindAndModifyOptions.options().upsert(true).returnNew(true),
          RequestCount.class);

      log.debug("Upserted request count for key={}", event.key());


    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn(
          "Skipping malformed tmdb.document.saved event at offset {}: {}", offset, e.getMessage());
    }
  }
}
