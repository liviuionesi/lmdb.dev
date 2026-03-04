package com.filmpire.shared.util;

import com.filmpire.shared.exception.ValidationException;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    /**
     * Validates that an object is not null
     *
     * @param obj       the object to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if object is null
     */
    public static void notNull(Object obj, String fieldName) {
        if (obj == null) {
            throw new ValidationException(fieldName, fieldName + " must not be null");
        }
    }

    /**
     * Validates that a string is not empty
     *
     * @param str       the string to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if string is empty
     */
    public static void notEmpty(String str, String fieldName) {
        if (StringUtils.isEmpty(str)) {
            throw new ValidationException(fieldName, fieldName + " must not be empty");
        }
    }

    /**
     * Validates that a string is not blank
     *
     * @param str       the string to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if string is blank
     */
    public static void notBlank(String str, String fieldName) {
        if (StringUtils.isBlank(str)) {
            throw new ValidationException(fieldName, fieldName + " must not be blank");
        }
    }

    /**
     * Validates that a collection is not empty
     *
     * @param collection the collection to validate
     * @param fieldName  the field name for error message
     * @throws ValidationException if collection is empty
     */
    public static void notEmpty(Collection<?> collection, String fieldName) {
        if (collection == null || collection.isEmpty()) {
            throw new ValidationException(fieldName, fieldName + " must not be empty");
        }
    }

    /**
     * Validates string length
     *
     * @param str       the string to validate
     * @param minLength minimum length (inclusive)
     * @param maxLength maximum length (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if length is invalid
     */
    public static void length(String str, int minLength, int maxLength, String fieldName) {
        if (str == null) {
            return;
        }
        int length = str.length();
        if (length < minLength || length > maxLength) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s length must be between %d and %d characters", fieldName, minLength, maxLength)
            );
        }
    }

    /**
     * Validates minimum string length
     *
     * @param str       the string to validate
     * @param minLength minimum length (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if length is less than minimum
     */
    public static void minLength(String str, int minLength, String fieldName) {
        if (str != null && str.length() < minLength) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must be at least %d characters long", fieldName, minLength)
            );
        }
    }

    /**
     * Validates maximum string length
     *
     * @param str       the string to validate
     * @param maxLength maximum length (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if length exceeds maximum
     */
    public static void maxLength(String str, int maxLength, String fieldName) {
        if (str != null && str.length() > maxLength) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must not exceed %d characters", fieldName, maxLength)
            );
        }
    }

    /**
     * Validates that a number is within a range
     *
     * @param value     the number to validate
     * @param min       minimum value (inclusive)
     * @param max       maximum value (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if value is out of range
     */
    public static void range(Number value, Number min, Number max, String fieldName) {
        if (value == null) {
            return;
        }
        double val = value.doubleValue();
        double minVal = min.doubleValue();
        double maxVal = max.doubleValue();
        
        if (val < minVal || val > maxVal) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must be between %s and %s", fieldName, min, max)
            );
        }
    }

    /**
     * Validates minimum value
     *
     * @param value     the number to validate
     * @param min       minimum value (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if value is less than minimum
     */
    public static void min(Number value, Number min, String fieldName) {
        if (value != null && value.doubleValue() < min.doubleValue()) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must be at least %s", fieldName, min)
            );
        }
    }

    /**
     * Validates maximum value
     *
     * @param value     the number to validate
     * @param max       maximum value (inclusive)
     * @param fieldName the field name for error message
     * @throws ValidationException if value exceeds maximum
     */
    public static void max(Number value, Number max, String fieldName) {
        if (value != null && value.doubleValue() > max.doubleValue()) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must not exceed %s", fieldName, max)
            );
        }
    }

    /**
     * Validates email format
     *
     * @param email     the email to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if email format is invalid
     */
    public static void email(String email, String fieldName) {
        if (email != null && !StringUtils.isValidEmail(email)) {
            throw new ValidationException(fieldName, fieldName + " must be a valid email address");
        }
    }

    /**
     * Validates URL format
     *
     * @param url       the URL to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if URL format is invalid
     */
    public static void url(String url, String fieldName) {
        if (url != null && !StringUtils.isValidUrl(url)) {
            throw new ValidationException(fieldName, fieldName + " must be a valid URL");
        }
    }

    /**
     * Validates password strength
     * Requirements: At least 8 characters, one uppercase, one lowercase, one digit, one special character
     *
     * @param password  the password to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if password doesn't meet requirements
     */
    public static void password(String password, String fieldName) {
        if (password == null) {
            return;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException(
                    fieldName,
                    fieldName + " must be at least 8 characters long and contain uppercase, lowercase, digit, and special character"
            );
        }
    }

    /**
     * Validates that a string matches a pattern
     *
     * @param str       the string to validate
     * @param pattern   the regex pattern
     * @param fieldName the field name for error message
     * @throws ValidationException if string doesn't match pattern
     */
    public static void pattern(String str, Pattern pattern, String fieldName) {
        if (str != null && !pattern.matcher(str).matches()) {
            throw new ValidationException(fieldName, fieldName + " has invalid format");
        }
    }

    /**
     * Validates that a value is positive
     *
     * @param value     the number to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if value is not positive
     */
    public static void positive(Number value, String fieldName) {
        if (value != null && value.doubleValue() <= 0) {
            throw new ValidationException(fieldName, fieldName + " must be positive");
        }
    }

    /**
     * Validates that a value is non-negative
     *
     * @param value     the number to validate
     * @param fieldName the field name for error message
     * @throws ValidationException if value is negative
     */
    public static void nonNegative(Number value, String fieldName) {
        if (value != null && value.doubleValue() < 0) {
            throw new ValidationException(fieldName, fieldName + " must not be negative");
        }
    }

    /**
     * Validates that a value is in a collection
     *
     * @param value      the value to validate
     * @param collection the allowed values
     * @param fieldName  the field name for error message
     * @throws ValidationException if value is not in collection
     */
    public static void in(Object value, Collection<?> collection, String fieldName) {
        if (value != null && !collection.contains(value)) {
            throw new ValidationException(
                    fieldName,
                    String.format("%s must be one of: %s", fieldName, collection)
            );
        }
    }

    /**
     * Validates that two values are equal
     *
     * @param value1     first value
     * @param value2     second value
     * @param fieldName1 first field name
     * @param fieldName2 second field name
     * @throws ValidationException if values are not equal
     */
    public static void equals(Object value1, Object value2, String fieldName1, String fieldName2) {
        if (value1 == null && value2 == null) {
            return;
        }
        if (value1 == null || !value1.equals(value2)) {
            throw new ValidationException(
                    fieldName1,
                    String.format("%s and %s must match", fieldName1, fieldName2)
            );
        }
    }

    /**
     * Validates all conditions and throws exception if any fail
     *
     * @param fieldErrors map of field names to error messages
     * @throws ValidationException if map is not empty
     */
    public static void validate(Map<String, String> fieldErrors) {
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            throw new ValidationException("Validation failed", fieldErrors);
        }
    }
}
















