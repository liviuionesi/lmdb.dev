package com.filmpire.actor.controller;

import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorImageDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.FilmographyPageDto;
import com.filmpire.actor.service.ActorService;
import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.dto.HalResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Native typed actor endpoints (issue #18): profile, filmography, search.
 * Uses the shared {@link ApiResponse} envelope like every native Filmpire
 * endpoint; the TMDB-shaped facade lives separately in
 * {@code PersonFacadeController}.
 */
@RestController
@RequestMapping("/api/v1/actors")
@RequiredArgsConstructor
@Tag(name = "Actors", description = "Actor profiles, filmography and search")
public class ActorController {

    private final ActorService actorService;

    /**
     * Returns a typed actor profile.
     *
     * @param id TMDB person id
     * @return 200 with the profile
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get actor details by TMDB person id")
    public ResponseEntity<ApiResponse<HalResource<ActorDto>>> getActor(@PathVariable Long id) {
        // HATEOAS: the profile advertises links to itself and its filmography
        // sub-resource, so a client discovers navigation instead of building URLs.
        HalResource<ActorDto> model = HalResource.of(actorService.getActor(id))
            .withLink("self", linkTo(methodOn(ActorController.class).getActor(id)).withSelfRel().getHref())
            .withLink("movies", linkTo(methodOn(ActorController.class).getFilmography(id, 1, 20)).withRel("movies").getHref())
            .withLink("images", linkTo(methodOn(ActorController.class).getImages(id)).withRel("images").getHref());
        return ok(model, "Actor retrieved");
    }

    /**
     * Returns a page of an actor's filmography, newest release first.
     *
     * <p>Paged here even though TMDB's own {@code movie_credits} is not: this
     * is Filmpire's native API, so it can page, while the facade keeps
     * serving TMDB's exact unpaginated shape.</p>
     *
     * @param id   TMDB person id
     * @param page 1-based page number
     * @param size credits per page
     * @return 200 with one page of cast credits
     */
    @GetMapping("/{id}/movies")
    @Operation(summary = "Get a page of an actor's filmography")
    public ResponseEntity<ApiResponse<FilmographyPageDto>> getFilmography(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(actorService.getFilmographyPage(id, page, size), "Filmography retrieved");
    }

    /**
     * Returns every profile image TMDB knows for an actor.
     *
     * @param id TMDB person id
     * @return 200 with image references (empty list if TMDB has none)
     */
    @GetMapping("/{id}/images")
    @Operation(summary = "Get an actor's profile images")
    public ResponseEntity<ApiResponse<List<ActorImageDto>>> getImages(@PathVariable Long id) {
        return ok(actorService.getImages(id), "Images retrieved");
    }

    /**
     * Returns the currently popular actors.
     *
     * <p>Mapped before {@code /{id}} by Spring's literal-over-template
     * precedence, so "popular" is never parsed as an id.</p>
     *
     * @param page TMDB page number (1-based)
     * @return 200 with paged summaries
     */
    @GetMapping("/popular")
    @Operation(summary = "Get popular actors")
    public ResponseEntity<ApiResponse<ActorSearchResponse>> getPopular(
            @RequestParam(defaultValue = "1") int page) {
        return ok(actorService.getPopular(page), "Popular actors retrieved");
    }

    /**
     * Searches actors by name.
     *
     * @param query free-text name query
     * @param page  TMDB page number (1-based)
     * @return 200 with paged summaries
     */
    @GetMapping("/search")
    @Operation(summary = "Search actors by name")
    public ResponseEntity<ApiResponse<ActorSearchResponse>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page) {
        return ok(actorService.search(query, page), "Search completed");
    }

    /**
     * Wraps a payload in the shared success envelope.
     *
     * @param data    payload
     * @param message human-readable outcome
     * @param <T>     payload type
     * @return 200 response
     */
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message, HttpStatus.OK.value()));
    }
}
