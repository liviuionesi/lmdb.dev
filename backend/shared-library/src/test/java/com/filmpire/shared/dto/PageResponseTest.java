package com.filmpire.shared.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PageResponse}.
 * <p>
 * This test class verifies the functionality of the PageResponse DTO, including:
 * <ul>
 *   <li>Page creation with content and pagination metadata</li>
 *   <li>Pagination flag calculations (first, last, hasNext, hasPrevious)</li>
 *   <li>Empty page creation</li>
 *   <li>Single page scenarios</li>
 *   <li>Middle and last page scenarios</li>
 * </ul>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 * @see PageResponse
 */
class PageResponseTest {

    /**
     * Tests that {@link PageResponse#of(List, int, int, long)} creates a page response
     * with correct content, pagination metadata, and calculated flags.
     * Verifies that all pagination properties are correctly set including page number,
     * page size, total elements, total pages, and navigation flags.
     */
    @Test
    void of_shouldCreatePageResponse() {
        List<String> content = Arrays.asList("item1", "item2", "item3");
        PageResponse<String> response = PageResponse.of(content, 0, 10, 23);
        
        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getPageNumber()).isZero();
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(23);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.isHasPrevious()).isFalse();
        assertThat(response.getNumberOfElements()).isEqualTo(3);
    }

    /**
     * Tests that a middle page (not first, not last) has correct pagination flags.
     * Verifies that both hasNext and hasPrevious are true for pages in the middle
     * of a paginated result set.
     */
    @Test
    void of_middlePage_shouldHaveCorrectPaginationFlags() {
        List<String> content = Arrays.asList("item1", "item2");
        PageResponse<String> response = PageResponse.of(content, 1, 10, 30);
        
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isFalse();
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.isHasPrevious()).isTrue();
    }

    /**
     * Tests that the last page has correct pagination flags.
     * Verifies that isLast is true, hasNext is false, and hasPrevious is true
     * for the final page in a paginated result set.
     */
    @Test
    void of_lastPage_shouldHaveCorrectPaginationFlags() {
        List<String> content = Arrays.asList("item1", "item2");
        PageResponse<String> response = PageResponse.of(content, 2, 10, 22);
        
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isTrue();
    }

    /**
     * Tests that {@link PageResponse#empty(int, int)} creates an empty page response
     * with zero content and correct pagination metadata.
     * Verifies that empty pages have zero total elements, zero total pages,
     * and all navigation flags set appropriately for an empty result set.
     */
    @Test
    void empty_shouldCreateEmptyPageResponse() {
        PageResponse<String> response = PageResponse.empty(0, 10);
        
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
        assertThat(response.getNumberOfElements()).isZero();
    }

    /**
     * Tests that a single page (where all results fit on one page) has correct pagination flags.
     * Verifies that when total elements fit within one page, both isFirst and isLast are true,
     * and both hasNext and hasPrevious are false.
     */
    @Test
    void of_singlePage_shouldHaveCorrectPaginationFlags() {
        List<String> content = Arrays.asList("item1", "item2", "item3");
        PageResponse<String> response = PageResponse.of(content, 0, 10, 3);
        
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
    }
}
