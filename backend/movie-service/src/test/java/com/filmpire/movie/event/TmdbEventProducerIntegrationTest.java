package com.filmpire.movie.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration verification for {@link TmdbEventProducer}, utilizing Testcontainers to execute
 * against a KRaft single-node Kafka broker.
 *
 * <p>Validates asynchronous event publication to topic {@code tmdb.document.saved} and confirms
 * resilience against broker outages, ensuring publication execution neither blocks caller threads
 * nor throws runtime exceptions when Kafka goes offline.
 */
class TmdbEventProducerIntegrationTest {

  private static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer("apache/kafka:3.8.0");

  @BeforeAll
  static void setupContainer() {
    KAFKA_CONTAINER.start();
  }

  @AfterAll
  static void teardownContainer() {
    KAFKA_CONTAINER.stop();
  }

  /**
   * Verifies that invoking save-through publishing transmits an intact JSON event to topic {@code
   * tmdb.document.saved} that can be reliably read and deserialized by subscribers.
   *
   * @throws Exception if JSON parsing or consumer record iteration fails
   */
  @Test
  @DisplayName("Should successfully publish event payload to topic when broker is reachable")
  void testSuccessfulPublication() throws Exception {
    // Given: A producer targeting our Testcontainers broker and an ObjectMapper for serialization
    Map<String, Object> producerProps =
        KafkaTestUtils.producerProps(KAFKA_CONTAINER.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    KafkaTemplate<String, String> kafkaTemplate =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    ObjectMapper objectMapper = new ObjectMapper();
    TmdbEventProducer producer = new TmdbEventProducer(kafkaTemplate, objectMapper);

    // When: An event is published for a specific TMDB movie resource
    String canonicalKey = "movie:634649";
    String endpointType = "MOVIE_DETAIL";
    String path = "/movie/634649";
    producer.publishDocumentSavedEvent(canonicalKey, endpointType, path);

    // Then: A consumer subscribed to the topic receives and verifies the JSON event payload
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(KAFKA_CONTAINER.getBootstrapServers(), "test-group", false);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(Collections.singletonList(TmdbEventProducer.TOPIC_TMDB_DOCUMENT_SAVED));

      ConsumerRecords<String, String> records =
          KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
      assertThat(records.isEmpty()).isFalse();

      ConsumerRecord<String, String> receivedRecord = records.iterator().next();
      assertThat(receivedRecord.key()).isEqualTo(canonicalKey);

      TmdbDocumentSavedEvent deserializedEvent =
          objectMapper.readValue(receivedRecord.value(), TmdbDocumentSavedEvent.class);
      assertThat(deserializedEvent.key()).isEqualTo(canonicalKey);
      assertThat(deserializedEvent.endpointType()).isEqualTo(endpointType);
      assertThat(deserializedEvent.path()).isEqualTo(path);
      assertThat(deserializedEvent.eventId()).isNotBlank();
      assertThat(deserializedEvent.timestamp()).isGreaterThan(0L);
    }
  }

  /**
   * Proves fault tolerance by attempting publication against an unreachable broker, confirming that
   * execution completes immediately without throwing runtime exceptions.
   */
  @Test
  @DisplayName("Should suppress exceptions and avoid blocking caller when broker is unreachable")
  void testBrokerDownResilience() {
    // Given: A producer configured with minimal timeouts targeting a non-existent port
    Map<String, Object> offlineProps = new HashMap<>();
    offlineProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
    offlineProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    offlineProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    offlineProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
    offlineProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500);
    offlineProps.put(ProducerConfig.RETRIES_CONFIG, 0);

    KafkaTemplate<String, String> offlineTemplate =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(offlineProps));
    TmdbEventProducer offlineProducer = new TmdbEventProducer(offlineTemplate, new ObjectMapper());

    // When & Then: Invoking publication against an offline port never throws or blocks
    long startTimestamp = System.currentTimeMillis();
    assertDoesNotThrow(
        () ->
            offlineProducer.publishDocumentSavedEvent(
                "movie:offline", "MOVIE_DETAIL", "/movie/offline"));
    long elapsedTimeMs = System.currentTimeMillis() - startTimestamp;

    assertThat(elapsedTimeMs).isLessThan(200L);
  }
}
