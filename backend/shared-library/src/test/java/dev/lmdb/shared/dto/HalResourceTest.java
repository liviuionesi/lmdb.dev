package dev.lmdb.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HalResource}.
 *
 * @see HalResource
 */
@DisplayName("HalResource Tests")
class HalResourceTest {

  /**
   * Verify that of() creates a HalResource holding the correct payload and initializes an empty
   * links map.
   */
  @Test
  @DisplayName("of() wraps content with no links")
  void of_shouldWrapContentWithNoLinks() {
    HalResource<String> resource = HalResource.of("payload");

    assertThat(resource.getContent()).isEqualTo("payload");
    assertThat(resource.getLinks()).isEmpty();
  }

  /**
   * Verify that withLink() appends a relation/link mapping and returns the same instance to support
   * builder-style chaining.
   */
  @Test
  @DisplayName("withLink() adds a relation and returns the same instance for chaining")
  void withLink_shouldAddRelationAndReturnSameInstance() {
    HalResource<String> resource = HalResource.of("payload");

    HalResource<String> result = resource.withLink("self", "/api/v1/movies/550");

    assertThat(result).isSameAs(resource);
    assertThat(resource.getLinks()).containsKey("self");
    assertThat(resource.getLinks().get("self").href()).isEqualTo("/api/v1/movies/550");
  }

  /**
   * Multiple links must all be retained, keyed by their own relation name — a real resource (e.g. a
   * movie) links to itself and to related resources at once.
   */
  @Test
  @DisplayName("Multiple withLink() calls accumulate distinct relations")
  void multipleWithLinkCalls_shouldAccumulateDistinctRelations() {
    HalResource<String> resource =
        HalResource.of("payload")
            .withLink("self", "/api/v1/movies/550")
            .withLink("credits", "/api/v1/movies/550/credits");

    assertThat(resource.getLinks()).hasSize(2);
    assertThat(resource.getLinks().get("credits").href()).isEqualTo("/api/v1/movies/550/credits");
  }
}
