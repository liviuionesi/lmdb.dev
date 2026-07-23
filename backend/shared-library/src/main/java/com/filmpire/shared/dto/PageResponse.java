package com.filmpire.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Generic paginated response wrapper for list endpoints.
 * Provides pagination metadata and data in a consistent structure.
 *
 * <p>{@code T} is bounded to {@link Serializable} deliberately. This type is
 * returned from {@code @Cacheable} service methods, and the Redis cache here
 * uses JDK serialization — so a page whose elements are not serializable fails
 * at runtime with {@code NotSerializableException} on the first cache write.
 * Bounding the parameter turns that into a compile error at the call site
 * instead (see ADR-011 and the movie-service model classes, which had to be
 * made {@code Serializable} after exactly that failure reached production).</p>
 *
 * @param <T> the type of items in the page; must be {@link Serializable}
 *            because pages are cached via JDK serialization
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T extends Serializable> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * List of items in the current page
     */
    private List<T> content;

    /**
     * Current page number (zero-indexed)
     */
    private int pageNumber;

    /**
     * Number of items per page
     */
    private int pageSize;

    /**
     * Total number of elements across all pages
     */
    private long totalElements;

    /**
     * Total number of pages
     */
    private int totalPages;

    /**
     * Whether this is the first page
     */
    private boolean first;

    /**
     * Whether this is the last page
     */
    private boolean last;

    /**
     * Whether there are more pages after this one
     */
    private boolean hasNext;

    /**
     * Whether there are pages before this one
     */
    private boolean hasPrevious;

    /**
     * Number of items in the current page
     */
    private int numberOfElements;

    /**
     * Creates a page response from pagination data
     *
     * @param content       list of items
     * @param pageNumber    current page number (zero-indexed)
     * @param pageSize      items per page
     * @param totalElements total number of items
     * @param <T>           data type
     * @return PageResponse
     */
    public static <T extends Serializable> PageResponse<T> of(
            List<T> content,
            int pageNumber,
            int pageSize,
            long totalElements) {
        
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        
        return PageResponse.<T>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(pageNumber == 0)
                .last(pageNumber >= totalPages - 1)
                .hasNext(pageNumber < totalPages - 1)
                .hasPrevious(pageNumber > 0)
                .numberOfElements(content.size())
                .build();
    }

    /**
     * Creates an empty page response
     *
     * @param pageNumber current page number
     * @param pageSize   items per page
     * @param <T>        data type
     * @return empty PageResponse
     */
    public static <T extends Serializable> PageResponse<T> empty(int pageNumber, int pageSize) {
        return PageResponse.<T>builder()
                .content(List.of())
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .hasNext(false)
                .hasPrevious(false)
                .numberOfElements(0)
                .build();
    }
}




