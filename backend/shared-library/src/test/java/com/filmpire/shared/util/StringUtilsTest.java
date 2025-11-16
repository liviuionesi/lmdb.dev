package com.filmpire.shared.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StringUtils}.
 * <p>
 * This test class verifies the functionality of the StringUtils utility class, including:
 * <ul>
 *   <li>String validation (empty, blank, not empty, not blank)</li>
 *   <li>Default value handling</li>
 *   <li>String transformation (trim, capitalize, case conversion)</li>
 *   <li>String truncation</li>
 *   <li>Format validation (email, URL)</li>
 *   <li>String masking for security</li>
 *   <li>String joining operations</li>
 *   <li>Character type checking (numeric, alphabetic, alphanumeric)</li>
 * </ul>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 * @see StringUtils
 */
class StringUtilsTest {

    /**
     * Tests that {@link StringUtils#isEmpty(String)} returns true for null or empty strings,
     * and false for non-empty strings.
     * Verifies correct identification of empty strings.
     */
    @Test
    void isEmpty_shouldReturnTrueForNullOrEmpty() {
        assertThat(StringUtils.isEmpty(null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty("text")).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isBlank(String)} returns true for null, empty,
     * or whitespace-only strings, and false for strings with content.
     * Verifies correct identification of blank strings.
     */
    @Test
    void isBlank_shouldReturnTrueForNullEmptyOrWhitespace() {
        assertThat(StringUtils.isBlank(null)).isTrue();
        assertThat(StringUtils.isBlank("")).isTrue();
        assertThat(StringUtils.isBlank("   ")).isTrue();
        assertThat(StringUtils.isBlank("text")).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isNotEmpty(String)} returns true for non-empty strings
     * and false for null or empty strings.
     * Verifies the inverse of isEmpty functionality.
     */
    @Test
    void isNotEmpty_shouldReturnTrueForNonEmptyStrings() {
        assertThat(StringUtils.isNotEmpty("text")).isTrue();
        assertThat(StringUtils.isNotEmpty(null)).isFalse();
        assertThat(StringUtils.isNotEmpty("")).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isNotBlank(String)} returns true for non-blank strings
     * and false for null, empty, or whitespace-only strings.
     * Verifies the inverse of isBlank functionality.
     */
    @Test
    void isNotBlank_shouldReturnTrueForNonBlankStrings() {
        assertThat(StringUtils.isNotBlank("text")).isTrue();
        assertThat(StringUtils.isNotBlank(null)).isFalse();
        assertThat(StringUtils.isNotBlank("   ")).isFalse();
    }

    /**
     * Tests that {@link StringUtils#defaultIfEmpty(String, String)} returns the default value
     * when the input is null or empty, otherwise returns the input string.
     * Verifies default value substitution for empty strings.
     */
    @Test
    void defaultIfEmpty_shouldReturnDefaultForEmpty() {
        assertThat(StringUtils.defaultIfEmpty(null, "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfEmpty("", "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfEmpty("text", "default")).isEqualTo("text");
    }

    /**
     * Tests that {@link StringUtils#defaultIfBlank(String, String)} returns the default value
     * when the input is null, empty, or blank, otherwise returns the input string.
     * Verifies default value substitution for blank strings.
     */
    @Test
    void defaultIfBlank_shouldReturnDefaultForBlank() {
        assertThat(StringUtils.defaultIfBlank(null, "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfBlank("   ", "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfBlank("text", "default")).isEqualTo("text");
    }

    /**
     * Tests that {@link StringUtils#trimToNull(String)} trims whitespace and returns null
     * if the result is empty or blank, otherwise returns the trimmed string.
     * Verifies trimming and null conversion behavior.
     */
    @Test
    void trimToNull_shouldTrimAndReturnNullForEmpty() {
        assertThat(StringUtils.trimToNull("  text  ")).isEqualTo("text");
        assertThat(StringUtils.trimToNull("   ")).isNull();
        assertThat(StringUtils.trimToNull(null)).isNull();
    }

    /**
     * Tests that {@link StringUtils#capitalize(String)} capitalizes the first letter
     * of a string while keeping the rest lowercase.
     * Verifies correct capitalization of strings including edge cases.
     */
    @Test
    void capitalize_shouldCapitalizeFirstLetter() {
        assertThat(StringUtils.capitalize("hello")).isEqualTo("Hello");
        assertThat(StringUtils.capitalize("H")).isEqualTo("H");
        assertThat(StringUtils.capitalize("")).isEmpty();
        assertThat(StringUtils.capitalize(null)).isNull();
    }

    /**
     * Tests that {@link StringUtils#toCamelCase(String)} converts strings with various
     * separators (underscore, hyphen, space) to camelCase format.
     * Verifies correct camelCase conversion.
     */
    @Test
    void toCamelCase_shouldConvertToCamelCase() {
        assertThat(StringUtils.toCamelCase("hello_world")).isEqualTo("helloWorld");
        assertThat(StringUtils.toCamelCase("hello-world")).isEqualTo("helloWorld");
        assertThat(StringUtils.toCamelCase("hello world")).isEqualTo("helloWorld");
        assertThat(StringUtils.toCamelCase("_hello_world")).isEqualTo("helloWorld");
        assertThat(StringUtils.toCamelCase("hello")).isEqualTo("hello");
        assertThat(StringUtils.toCamelCase(null)).isNull();
        assertThat(StringUtils.toCamelCase("")).isEmpty();
    }

    /**
     * Tests that {@link StringUtils#toSnakeCase(String)} converts camelCase or kebab-case
     * strings to snake_case format.
     * Verifies correct snake_case conversion.
     */
    @Test
    void toSnakeCase_shouldConvertToSnakeCase() {
        assertThat(StringUtils.toSnakeCase("helloWorld")).isEqualTo("hello_world");
        assertThat(StringUtils.toSnakeCase("HelloWorld")).isEqualTo("hello_world");
        assertThat(StringUtils.toSnakeCase("hello-world")).isEqualTo("hello_world");
        assertThat(StringUtils.toSnakeCase("hello")).isEqualTo("hello");
        assertThat(StringUtils.toSnakeCase(null)).isNull();
        assertThat(StringUtils.toSnakeCase("")).isEmpty();
    }

    /**
     * Tests that {@link StringUtils#truncate(String, int)} truncates a string to the
     * specified maximum length.
     * Verifies correct truncation behavior including null handling.
     */
    @Test
    void truncate_shouldTruncateToMaxLength() {
        assertThat(StringUtils.truncate("hello world", 5)).isEqualTo("hello");
        assertThat(StringUtils.truncate("hi", 10)).isEqualTo("hi");
        assertThat(StringUtils.truncate(null, 5)).isNull();
    }

    /**
     * Tests that {@link StringUtils#truncateWithEllipsis(String, int)} truncates a string
     * and appends ellipsis when the string exceeds the maximum length.
     * Verifies ellipsis addition for truncated strings.
     */
    @Test
    void truncateWithEllipsis_shouldAddEllipsis() {
        assertThat(StringUtils.truncateWithEllipsis("hello world", 8)).isEqualTo("hello...");
        assertThat(StringUtils.truncateWithEllipsis("hi", 10)).isEqualTo("hi");
        assertThat(StringUtils.truncateWithEllipsis("hello", 2)).isEqualTo("he");
        assertThat(StringUtils.truncateWithEllipsis("hello", 1)).isEqualTo("h");
        assertThat(StringUtils.truncateWithEllipsis(null, 5)).isNull();
    }

    /**
     * Tests that {@link StringUtils#isValidEmail(String)} validates email addresses
     * using a standard email pattern.
     * Verifies correct email validation including various valid and invalid formats.
     */
    @Test
    void isValidEmail_shouldValidateEmailFormat() {
        assertThat(StringUtils.isValidEmail("test@example.com")).isTrue();
        assertThat(StringUtils.isValidEmail("user.name@domain.co.uk")).isTrue();
        assertThat(StringUtils.isValidEmail("invalid-email")).isFalse();
        assertThat(StringUtils.isValidEmail("@example.com")).isFalse();
        assertThat(StringUtils.isValidEmail(null)).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isValidUrl(String)} validates URLs using a standard
     * URL pattern supporting http, https, and ftp protocols.
     * Verifies correct URL validation including various valid and invalid formats.
     */
    @Test
    void isValidUrl_shouldValidateUrlFormat() {
        assertThat(StringUtils.isValidUrl("https://example.com")).isTrue();
        assertThat(StringUtils.isValidUrl("http://example.com/path")).isTrue();
        assertThat(StringUtils.isValidUrl("ftp://ftp.example.com")).isTrue();
        assertThat(StringUtils.isValidUrl("not-a-url")).isFalse();
        assertThat(StringUtils.isValidUrl(null)).isFalse();
    }

    /**
     * Tests that {@link StringUtils#mask(String, int, char)} masks middle characters
     * of a string while preserving the specified number of characters at the start and end.
     * Verifies correct string masking for security purposes.
     */
    @Test
    void mask_shouldMaskMiddleCharacters() {
        assertThat(StringUtils.mask("1234567890", 2, '*')).isEqualTo("12******90");
        assertThat(StringUtils.mask("123", 2, '*')).isEqualTo("123");
    }

    /**
     * Tests that {@link StringUtils#maskEmail(String)} masks email addresses
     * by hiding parts of the local and domain sections.
     * Verifies correct email masking for privacy protection.
     */
    @Test
    void maskEmail_shouldMaskEmailAddress() {
        assertThat(StringUtils.maskEmail("john@example.com")).isEqualTo("jo***@ex***.com");
        assertThat(StringUtils.maskEmail("invalid")).isEqualTo("invalid");
        assertThat(StringUtils.maskEmail("ab@cd.com")).isEqualTo("ab***@cd***.com");
        assertThat(StringUtils.maskEmail("a@b.com")).isEqualTo("a***@b***.com");
        assertThat(StringUtils.maskEmail("test@x.co.uk")).isEqualTo("te***@x***.co.uk");
    }

    /**
     * Tests that {@link StringUtils#join(String, String...)} joins an array of strings
     * using the specified delimiter.
     * Verifies correct string joining with various delimiters.
     */
    @Test
    void join_withArray_shouldJoinStrings() {
        assertThat(StringUtils.join(", ", "a", "b", "c")).isEqualTo("a, b, c");
        assertThat(StringUtils.join("-", "hello", "world")).isEqualTo("hello-world");
        assertThat(StringUtils.join(", ", "a", null, "c")).isEqualTo("a, c");
        assertThat(StringUtils.join(", ")).isEmpty();
    }

    /**
     * Tests that {@link StringUtils#join(String, List)} joins a list of strings
     * using the specified delimiter.
     * Verifies correct string joining from collections.
     */
    @Test
    void join_withList_shouldJoinStrings() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertThat(StringUtils.join(", ", list)).isEqualTo("a, b, c");
        List<String> listWithNull = Arrays.asList("a", null, "c");
        assertThat(StringUtils.join(", ", listWithNull)).isEqualTo("a, c");
        assertThat(StringUtils.join(", ", Arrays.asList())).isEmpty();
        assertThat(StringUtils.join(", ", (List<String>) null)).isEmpty();
    }

    /**
     * Tests that {@link StringUtils#removeWhitespace(String)} removes all whitespace
     * characters from a string.
     * Verifies correct whitespace removal.
     */
    @Test
    void removeWhitespace_shouldRemoveAllWhitespace() {
        assertThat(StringUtils.removeWhitespace("hello world")).isEqualTo("helloworld");
        assertThat(StringUtils.removeWhitespace("  a  b  c  ")).isEqualTo("abc");
        assertThat(StringUtils.removeWhitespace(null)).isNull();
        assertThat(StringUtils.removeWhitespace("")).isEmpty();
    }

    /**
     * Tests that {@link StringUtils#isNumeric(String)} returns true only for strings
     * containing digits exclusively.
     * Verifies correct numeric string validation.
     */
    @Test
    void isNumeric_shouldReturnTrueForDigitsOnly() {
        assertThat(StringUtils.isNumeric("12345")).isTrue();
        assertThat(StringUtils.isNumeric("123abc")).isFalse();
        assertThat(StringUtils.isNumeric("")).isFalse();
        assertThat(StringUtils.isNumeric(null)).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isAlphabetic(String)} returns true only for strings
     * containing letters exclusively.
     * Verifies correct alphabetic string validation.
     */
    @Test
    void isAlphabetic_shouldReturnTrueForLettersOnly() {
        assertThat(StringUtils.isAlphabetic("hello")).isTrue();
        assertThat(StringUtils.isAlphabetic("hello123")).isFalse();
        assertThat(StringUtils.isAlphabetic("")).isFalse();
        assertThat(StringUtils.isAlphabetic(null)).isFalse();
    }

    /**
     * Tests that {@link StringUtils#isAlphanumeric(String)} returns true only for strings
     * containing letters and digits exclusively (no special characters).
     * Verifies correct alphanumeric string validation.
     */
    @Test
    void isAlphanumeric_shouldReturnTrueForLettersAndDigits() {
        assertThat(StringUtils.isAlphanumeric("hello123")).isTrue();
        assertThat(StringUtils.isAlphanumeric("hello-123")).isFalse();
        assertThat(StringUtils.isAlphanumeric("")).isFalse();
    }
}
