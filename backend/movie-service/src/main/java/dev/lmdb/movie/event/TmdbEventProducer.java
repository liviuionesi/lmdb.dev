package dev.lmdb.movie.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Asynchronous producer responsible for emitting TMDB save-through notifications to Kafka topic
 * {@code tmdb.document.saved}.
 *
 * <p>Event publication occurs asynchronously on a background thread, ensuring that broker
 * unavailability or slow network IO never impacts latency or stability of caller threads. Any Kafka
 * producer exceptions are logged at WARN level and suppressed without throwing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TmdbEventProducer {

  public static final String TOPIC_TMDB_DOCUMENT_SAVED = "tmdb.document.saved";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  /**
   * Asynchronously constructs and transmits a {@link TmdbDocumentSavedEvent} to the Kafka cluster.
   *
   * @param canonicalKey canonical resource routing key
   * @param endpointType functional categorization of the invoked facade endpoint
   * @param path relative request path executed by the client
   */
  public void publishDocumentSavedEvent(String canonicalKey, String endpointType, String path) {
    // 1. Construct immutable event payload with unique UUID for consumer deduplication
    TmdbDocumentSavedEvent event =
        TmdbDocumentSavedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .key(canonicalKey)
            .endpointType(endpointType)
            .path(path)
            .timestamp(System.currentTimeMillis())
            .build();

    // 2. Dispatch asynchronously via CompletableFuture to isolate HTTP request threads from broker
    // delays
    CompletableFuture.runAsync(
        () -> {
          try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate
                .send(TOPIC_TMDB_DOCUMENT_SAVED, canonicalKey, jsonPayload)
                .whenComplete(
                    (result, ex) -> {
                      if (ex != null) {
                        log.warn(
                            "Failed to deliver save-through event to topic {} for key {}: {}",
                            TOPIC_TMDB_DOCUMENT_SAVED,
                            canonicalKey,
                            ex.getMessage());
                      } else {
                        log.debug(
                            "Successfully emitted event {} to partition {} at offset {}",
                            event.eventId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                      }
                    });
          } catch (Exception e) {
            log.warn(
                "Exception suppressed while dispatching save-through event for key {}: {}",
                canonicalKey,
                e.getMessage());
          }
        });
  }
}
