package com.filmpire.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decorates a single-resource payload with HAL-style {@code _links} while
 * staying inside the shared {@link ApiResponse} envelope.
 *
 * <p>Spring HATEOAS's own {@code EntityModel} only gets HAL treatment
 * ({@code _links} as a rel-keyed map) from Spring Boot's hypermedia message
 * converter when it is the controller method's top-level return type; nested
 * inside {@code ApiResponse<T>} it silently falls back to plain Jackson bean
 * serialization, i.e. a {@code "links": [...]} array instead of
 * {@code "_links": {...}}. This type sidesteps that by unwrapping the
 * content's own fields ({@link JsonUnwrapped}) and serializing a hand-built
 * {@code _links} map alongside them — the caller still uses Spring HATEOAS's
 * {@code linkTo}/{@code methodOn} to build each href, only the wrapping
 * differs.
 *
 * @param <T> the wrapped resource type
 * @see ApiResponse
 */
public class HalResource<T> {

    @JsonUnwrapped
    private final T content;

    @JsonProperty("_links")
    private final Map<String, Href> links = new LinkedHashMap<>();

    private HalResource(T content) {
        this.content = content;
    }

    /**
     * Wraps a resource with no links yet.
     *
     * @param content the resource payload
     * @param <T>     resource type
     * @return a new, link-less HalResource; add links with {@link #withLink}
     */
    public static <T> HalResource<T> of(T content) {
        return new HalResource<>(content);
    }

    /**
     * Adds a named relation link.
     *
     * @param rel  the link relation name (e.g. {@code "self"}, {@code "movies"})
     * @param href the target URI
     * @return this instance, for fluent chaining
     */
    public HalResource<T> withLink(String rel, String href) {
        links.put(rel, new Href(href));
        return this;
    }

    /**
     * @return the wrapped resource payload
     */
    public T getContent() {
        return content;
    }

    /**
     * @return the resource's links, keyed by relation name
     */
    public Map<String, Href> getLinks() {
        return links;
    }

    /**
     * A single HAL link target — just the href, matching the subset of the
     * HAL spec this codebase currently uses (no templated or typed links).
     *
     * @param href the target URI
     */
    public record Href(String href) {
    }
}
