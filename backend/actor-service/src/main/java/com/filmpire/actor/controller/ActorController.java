package com.filmpire.actor.controller;

import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.dto.ActorDtos.ActorSearchResponse;
import com.filmpire.actor.dto.ActorDtos.FilmographyEntryDto;
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
            .withLink("movies", linkTo(methodOn(ActorController.class).getFilmography(id)).withRel("movies").getHref());
        return ok(model, "Actor retrieved");
    }

    /**
     * Returns an actor's filmography, newest release first.
     *
     * @param id TMDB person id
     * @return 200 with cast credits
     */
    @GetMapping("/{id}/movies")
    @Operation(summary = "Get an actor's filmography")
    public ResponseEntity<ApiResponse<List<FilmographyEntryDto>>> getFilmography(
            @PathVariable Long id) {
        return ok(actorService.getFilmography(id), "Filmography retrieved");
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
