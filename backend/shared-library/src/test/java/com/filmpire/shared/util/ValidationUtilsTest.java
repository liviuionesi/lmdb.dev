package com.filmpire.shared.util;

import com.filmpire.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ValidationUtils}.
 * <p>
 * This test class verifies the functionality of the ValidationUtils utility class, including:
 * <ul>
 *   <li>Null, empty, and blank validation</li>
 *   <li>String length validation (min, max, range)</li>
 *   <li>Numeric range validation</li>
 *   <li>Format validation (email, URL, password)</li>
 *   <li>Value comparison and membership validation</li>
 *   <li>Collection validation</li>
 *   <li>Aggregate validation with error collection</li>
 *   <li>Null-safe behavior</li>
 * </ul>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 * @see ValidationUtils
 */
class ValidationUtilsTest {

    /**
     * Tests that {@link ValidationUtils#notNull(Object, String)} throws a ValidationException
     * when the value is null.
     * Verifies that null values are properly rejected with an appropriate error message.
     */
    @Test
    void notNull_shouldThrowForNullValue() {
        assertThatThrownBy(() -> ValidationUtils.notNull(null, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be null");
    }

    /**
     * Tests that {@link ValidationUtils#notNull(Object, String)} does not throw an exception
     * when the value is not null.
     * Verifies that non-null values pass validation successfully.
     */
    @Test
    void notNull_shouldNotThrowForNonNullValue() {
        assertThatCode(() -> ValidationUtils.notNull("value", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#notEmpty(String, String)} throws a ValidationException
     * when the string is empty.
     * Verifies that empty strings are properly rejected.
     */
    @Test
    void notEmpty_shouldThrowForEmptyString() {
        assertThatThrownBy(() -> ValidationUtils.notEmpty("", "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be empty");
    }

    /**
     * Tests that {@link ValidationUtils#notEmpty(String, String)} does not throw an exception
     * when the string is not empty.
     * Verifies that non-empty strings pass validation successfully.
     */
    @Test
    void notEmpty_shouldNotThrowForNonEmptyString() {
        assertThatCode(() -> ValidationUtils.notEmpty("value", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#notBlank(String, String)} throws a ValidationException
     * when the string is blank (whitespace only).
     * Verifies that blank strings are properly rejected.
     */
    @Test
    void notBlank_shouldThrowForBlankString() {
        assertThatThrownBy(() -> ValidationUtils.notBlank("   ", "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be blank");
    }

    /**
     * Tests that {@link ValidationUtils#notBlank(String, String)} does not throw an exception
     * when the string is not blank.
     * Verifies that non-blank strings pass validation successfully.
     */
    @Test
    void notBlank_shouldNotThrowForNonBlankString() {
        assertThatCode(() -> ValidationUtils.notBlank("value", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#notEmpty(java.util.Collection, String)} throws a
     * ValidationException when the collection is empty.
     * Verifies that empty collections are properly rejected.
     */
    @Test
    void notEmpty_collection_shouldThrowForEmptyCollection() {
        assertThatThrownBy(() -> ValidationUtils.notEmpty(Collections.emptyList(), "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be empty");
    }

    /**
     * Tests that {@link ValidationUtils#notEmpty(java.util.Collection, String)} does not throw
     * an exception when the collection is not empty.
     * Verifies that non-empty collections pass validation successfully.
     */
    @Test
    void notEmpty_collection_shouldNotThrowForNonEmptyCollection() {
        assertThatCode(() -> ValidationUtils.notEmpty(Arrays.asList("a", "b"), "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#length(String, int, int, String)} throws a
     * ValidationException when the string length is outside the specified range.
     * Verifies that strings with invalid lengths are properly rejected.
     */
    @Test
    void length_shouldThrowForInvalidLength() {
        assertThatThrownBy(() -> ValidationUtils.length("hi", 5, 10, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("length must be between");
    }

    /**
     * Tests that {@link ValidationUtils#length(String, int, int, String)} does not throw
     * an exception when the string length is within the specified range.
     * Verifies that strings with valid lengths pass validation successfully.
     */
    @Test
    void length_shouldNotThrowForValidLength() {
        assertThatCode(() -> ValidationUtils.length("hello", 3, 10, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#minLength(String, int, String)} throws a
     * ValidationException when the string length is below the minimum.
     * Verifies that strings shorter than the minimum length are properly rejected.
     */
    @Test
    void minLength_shouldThrowForShortString() {
        assertThatThrownBy(() -> ValidationUtils.minLength("hi", 5, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be at least");
    }

    /**
     * Tests that {@link ValidationUtils#minLength(String, int, String)} does not throw
     * an exception when the string length meets or exceeds the minimum.
     * Verifies that strings with sufficient length pass validation successfully.
     */
    @Test
    void minLength_shouldNotThrowForValidLength() {
        assertThatCode(() -> ValidationUtils.minLength("hello", 3, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#maxLength(String, int, String)} throws a
     * ValidationException when the string length exceeds the maximum.
     * Verifies that strings longer than the maximum length are properly rejected.
     */
    @Test
    void maxLength_shouldThrowForLongString() {
        assertThatThrownBy(() -> ValidationUtils.maxLength("hello world", 5, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not exceed");
    }

    /**
     * Tests that {@link ValidationUtils#maxLength(String, int, String)} does not throw
     * an exception when the string length is within the maximum limit.
     * Verifies that strings within the length limit pass validation successfully.
     */
    @Test
    void maxLength_shouldNotThrowForValidLength() {
        assertThatCode(() -> ValidationUtils.maxLength("hello", 10, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#range(Number, Number, Number, String)} throws a
     * ValidationException when the value is outside the specified range.
     * Verifies that values outside the valid range are properly rejected.
     */
    @Test
    void range_shouldThrowForValueOutOfRange() {
        assertThatThrownBy(() -> ValidationUtils.range(15, 1, 10, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be between");
    }

    /**
     * Tests that {@link ValidationUtils#range(Number, Number, Number, String)} does not throw
     * an exception when the value is within the specified range.
     * Verifies that values within the valid range pass validation successfully.
     */
    @Test
    void range_shouldNotThrowForValueInRange() {
        assertThatCode(() -> ValidationUtils.range(5, 1, 10, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#min(Number, Number, String)} throws a
     * ValidationException when the value is below the minimum.
     * Verifies that values below the minimum are properly rejected.
     */
    @Test
    void min_shouldThrowForValueBelowMinimum() {
        assertThatThrownBy(() -> ValidationUtils.min(3, 5, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be at least");
    }

    /**
     * Tests that {@link ValidationUtils#min(Number, Number, String)} does not throw
     * an exception when the value meets or exceeds the minimum.
     * Verifies that values meeting the minimum requirement pass validation successfully.
     */
    @Test
    void min_shouldNotThrowForValidValue() {
        assertThatCode(() -> ValidationUtils.min(10, 5, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#max(Number, Number, String)} throws a
     * ValidationException when the value exceeds the maximum.
     * Verifies that values above the maximum are properly rejected.
     */
    @Test
    void max_shouldThrowForValueAboveMaximum() {
        assertThatThrownBy(() -> ValidationUtils.max(15, 10, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not exceed");
    }

    /**
     * Tests that {@link ValidationUtils#max(Number, Number, String)} does not throw
     * an exception when the value is within the maximum limit.
     * Verifies that values within the maximum limit pass validation successfully.
     */
    @Test
    void max_shouldNotThrowForValidValue() {
        assertThatCode(() -> ValidationUtils.max(5, 10, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#email(String, String)} throws a ValidationException
     * when the email format is invalid.
     * Verifies that invalid email addresses are properly rejected.
     */
    @Test
    void email_shouldThrowForInvalidEmail() {
        assertThatThrownBy(() -> ValidationUtils.email("invalid-email", "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be a valid email");
    }

    /**
     * Tests that {@link ValidationUtils#email(String, String)} does not throw an exception
     * when the email format is valid.
     * Verifies that valid email addresses pass validation successfully.
     */
    @Test
    void email_shouldNotThrowForValidEmail() {
        assertThatCode(() -> ValidationUtils.email("test@example.com", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#url(String, String)} throws a ValidationException
     * when the URL format is invalid.
     * Verifies that invalid URLs are properly rejected.
     */
    @Test
    void url_shouldThrowForInvalidUrl() {
        assertThatThrownBy(() -> ValidationUtils.url("not-a-url", "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be a valid URL");
    }

    /**
     * Tests that {@link ValidationUtils#url(String, String)} does not throw an exception
     * when the URL format is valid.
     * Verifies that valid URLs pass validation successfully.
     */
    @Test
    void url_shouldNotThrowForValidUrl() {
        assertThatCode(() -> ValidationUtils.url("https://example.com", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#password(String, String)} throws a ValidationException
     * when the password does not meet strength requirements (minimum 8 characters).
     * Verifies that weak passwords are properly rejected.
     */
    @Test
    void password_shouldThrowForWeakPassword() {
        assertThatThrownBy(() -> ValidationUtils.password("weak", "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be at least 8 characters");
    }

    /**
     * Tests that {@link ValidationUtils#password(String, String)} does not throw an exception
     * when the password meets strength requirements.
     * Verifies that strong passwords pass validation successfully.
     */
    @Test
    void password_shouldNotThrowForStrongPassword() {
        assertThatCode(() -> ValidationUtils.password("Strong@Pass123", "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#positive(Number, String)} throws a ValidationException
     * when the value is zero or negative.
     * Verifies that non-positive values are properly rejected.
     */
    @Test
    void positive_shouldThrowForZeroOrNegative() {
        assertThatThrownBy(() -> ValidationUtils.positive(0, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be positive");
        
        assertThatThrownBy(() -> ValidationUtils.positive(-5, "field"))
                .isInstanceOf(ValidationException.class);
    }

    /**
     * Tests that {@link ValidationUtils#positive(Number, String)} does not throw an exception
     * when the value is positive.
     * Verifies that positive values pass validation successfully.
     */
    @Test
    void positive_shouldNotThrowForPositiveValue() {
        assertThatCode(() -> ValidationUtils.positive(5, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#nonNegative(Number, String)} throws a ValidationException
     * when the value is negative.
     * Verifies that negative values are properly rejected.
     */
    @Test
    void nonNegative_shouldThrowForNegativeValue() {
        assertThatThrownBy(() -> ValidationUtils.nonNegative(-1, "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be negative");
    }

    /**
     * Tests that {@link ValidationUtils#nonNegative(Number, String)} does not throw an exception
     * when the value is zero or positive.
     * Verifies that non-negative values pass validation successfully.
     */
    @Test
    void nonNegative_shouldNotThrowForZeroOrPositive() {
        assertThatCode(() -> ValidationUtils.nonNegative(0, "field"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidationUtils.nonNegative(5, "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#in(Object, java.util.Collection, String)} throws a
     * ValidationException when the value is not in the specified collection.
     * Verifies that values outside the allowed set are properly rejected.
     */
    @Test
    void in_shouldThrowForValueNotInCollection() {
        assertThatThrownBy(() -> ValidationUtils.in("d", Arrays.asList("a", "b", "c"), "field"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be one of");
    }

    /**
     * Tests that {@link ValidationUtils#in(Object, java.util.Collection, String)} does not throw
     * an exception when the value is in the specified collection.
     * Verifies that values within the allowed set pass validation successfully.
     */
    @Test
    void in_shouldNotThrowForValueInCollection() {
        assertThatCode(() -> ValidationUtils.in("b", Arrays.asList("a", "b", "c"), "field"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#equals(Object, Object, String, String)} throws a
     * ValidationException when the two values do not match.
     * Verifies that mismatched values are properly rejected (e.g., password confirmation).
     */
    @Test
    void equals_shouldThrowForNonMatchingValues() {
        assertThatThrownBy(() -> ValidationUtils.equals("value1", "value2", "field1", "field2"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must match");
    }

    /**
     * Tests that {@link ValidationUtils#equals(Object, Object, String, String)} does not throw
     * an exception when the two values match.
     * Verifies that matching values pass validation successfully.
     */
    @Test
    void equals_shouldNotThrowForMatchingValues() {
        assertThatCode(() -> ValidationUtils.equals("value", "value", "field1", "field2"))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that {@link ValidationUtils#validate(Map)} throws a ValidationException when the
     * error map contains validation errors.
     * Verifies that aggregate validation properly throws when errors are present.
     */
    @Test
    void validate_shouldThrowForNonEmptyFieldErrors() {
        Map<String, String> errors = new HashMap<>();
        errors.put("field1", "error1");
        errors.put("field2", "error2");
        
        assertThatThrownBy(() -> ValidationUtils.validate(errors))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Validation failed");
    }

    /**
     * Tests that {@link ValidationUtils#validate(Map)} does not throw an exception when the
     * error map is empty.
     * Verifies that aggregate validation passes when no errors are present.
     */
    @Test
    void validate_shouldNotThrowForEmptyFieldErrors() {
        assertThatCode(() -> ValidationUtils.validate(new HashMap<>()))
                .doesNotThrowAnyException();
    }

    /**
     * Tests that various ValidationUtils methods handle null inputs gracefully
     * without throwing exceptions.
     * Verifies null-safe behavior across multiple validation methods including
     * length, email, and numeric validations.
     */
    @Test
    void nullInputs_shouldHandleGracefully() {
        assertThatCode(() -> ValidationUtils.length(null, 1, 10, "field"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidationUtils.email(null, "field"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidationUtils.positive(null, "field"))
                .doesNotThrowAnyException();
    }
}
