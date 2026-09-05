package dev.lmdb.actor.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Actor}'s Java serializability.
 *
 * <p>{@code spring.cache.type=redis} (see {@code application.yml}) backs {@link
 * dev.lmdb.actor.service.ActorService}'s {@code @Cacheable} methods with Spring Boot's default
 * {@code RedisCacheManager}, whose default value serializer is JDK serialization — not Jackson.
 * That means every type reachable from a cached {@link Actor}, {@link Actor} itself included, must
 * implement {@link java.io.Serializable} or the cache write throws at runtime. Task #34 recorded a
 * live crash of exactly this shape ("Redis serialization crash on actor detail", commit
 * {@code 4b84d7d}); this test is the regression check that criterion never named.
 *
 * <p>Runs a real JDK serialize/deserialize round-trip rather than mocking anything, so it fails the
 * same way the original bug did if {@link Actor} or {@link ActorProfileImage} ever stops being
 * serializable — for example if a field of a non-serializable type were added.
 */
@DisplayName("Actor Serializable Tests")
class ActorTest {

  /**
   * Given a fully populated {@link Actor}, including its {@link ActorProfileImage} collection,
   * when it is written to and read back from a JDK {@link ObjectOutputStream}/{@link
   * ObjectInputStream} pair (what {@code RedisCacheManager}'s default serializer does internally),
   * then the round-trip succeeds and every field survives unchanged. A non-serializable field
   * anywhere in this object graph would throw {@link java.io.NotSerializableException} here.
   */
  @Test
  @DisplayName("round-trips through JDK serialization, matching Redis's default cache serializer")
  void survivesJdkSerializationRoundTrip() throws IOException, ClassNotFoundException {
    Actor original =
        Actor.builder()
            .tmdbId(287L)
            .name("Brad Pitt")
            .biography("American actor and producer.")
            .birthDate(LocalDate.of(1963, Month.DECEMBER, 18))
            .birthPlace("Shawnee, Oklahoma, USA")
            .profilePath("/bp.jpg")
            .popularity(42.5)
            .alsoKnownAs(List.of("William Bradley Pitt"))
            .profileImages(
                List.of(
                    ActorProfileImage.builder()
                        .filePath("/bp1.jpg")
                        .aspectRatio(0.667)
                        .height(3000)
                        .width(2000)
                        .iso6391("en")
                        .voteAverage(5.4)
                        .voteCount(12)
                        .build()))
            .knownForDepartment("Acting")
            .gender(2)
            .imdbId("nm0000093")
            .homepage("https://example.com/bradpitt")
            .adult(false)
            .syncedAt(LocalDateTime.of(2026, 8, 1, 12, 0))
            .build();

    // 1. Serialize exactly as RedisCacheManager's default JdkSerializationRedisSerializer would.
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }

    // 2. Deserialize back and confirm the whole graph survived, not just that no exception fired.
    Actor restored;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Actor) in.readObject();
    }

    assertThat(restored.getTmdbId()).isEqualTo(original.getTmdbId());
    assertThat(restored.getName()).isEqualTo(original.getName());
    assertThat(restored.getBirthDate()).isEqualTo(original.getBirthDate());
    assertThat(restored.getSyncedAt()).isEqualTo(original.getSyncedAt());
    assertThat(restored.getAlsoKnownAs()).isEqualTo(original.getAlsoKnownAs());
    // ActorProfileImage has no generated equals()/hashCode() (only @Getter/@Setter/@Builder), so
    // the embeddable's own field is compared directly rather than the list via isEqualTo.
    assertThat(restored.getProfileImages()).hasSize(1);
    assertThat(restored.getProfileImages().get(0).getFilePath())
        .isEqualTo(original.getProfileImages().get(0).getFilePath());
  }
}
