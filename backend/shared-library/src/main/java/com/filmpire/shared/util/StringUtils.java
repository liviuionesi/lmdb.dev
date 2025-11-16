package com.filmpire.shared.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for string operations.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$"
    );

    /**
     * Checks if a string is null or empty
     *
     * @param str the string to check
     * @return true if the string is null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace
     *
     * @param str the string to check
     * @return true if the string is blank
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if a string is not empty
     *
     * @param str the string to check
     * @return true if the string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Checks if a string is not blank
     *
     * @param str the string to check
     * @return true if the string is not null and contains non-whitespace characters
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Returns a default value if the string is null or empty
     *
     * @param str          the string to check
     * @param defaultValue the default value to return
     * @return the string if not empty, otherwise the default value
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * Returns a default value if the string is null or blank
     *
     * @param str          the string to check
     * @param defaultValue the default value to return
     * @return the string if not blank, otherwise the default value
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    /**
     * Trims a string, returning null if the result is empty
     *
     * @param str the string to trim
     * @return trimmed string or null
     */
    public static String trimToNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Capitalizes the first letter of a string
     *
     * @param str the string to capitalize
     * @return capitalized string
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Converts a string to camelCase
     *
     * @param str the string to convert
     * @return camelCase string
     */
    public static String toCamelCase(String str) {
        if (isEmpty(str)) {
            return str;
        }
        String[] parts = str.split("[\\s_-]+");
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (first) {
                    result.append(part.toLowerCase());
                    first = false;
                } else {
                    result.append(capitalize(part.toLowerCase()));
                }
            }
        }
        return result.toString();
    }

    /**
     * Converts a string to snake_case
     *
     * @param str the string to convert
     * @return snake_case string
     */
    public static String toSnakeCase(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[\\s-]+", "_")
                .toLowerCase();
    }

    /**
     * Truncates a string to a specified length
     *
     * @param str       the string to truncate
     * @param maxLength maximum length
     * @return truncated string
     */
    public static String truncate(String str, int maxLength) {
        if (isEmpty(str) || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * Truncates a string and adds ellipsis
     *
     * @param str       the string to truncate
     * @param maxLength maximum length (including ellipsis)
     * @return truncated string with ellipsis
     */
    public static String truncateWithEllipsis(String str, int maxLength) {
        if (isEmpty(str) || str.length() <= maxLength) {
            return str;
        }
        if (maxLength < 3) {
            return str.substring(0, maxLength);
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Validates if a string is a valid email address
     *
     * @param email the string to validate
     * @return true if valid email format
     */
    public static boolean isValidEmail(String email) {
        if (isEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates if a string is a valid URL
     *
     * @param url the string to validate
     * @return true if valid URL format
     */
    public static boolean isValidUrl(String url) {
        if (isEmpty(url)) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }

    /**
     * Masks a string, showing only the first and last N characters
     *
     * @param str            the string to mask
     * @param visibleChars   number of visible characters at start and end
     * @param maskCharacter  character to use for masking
     * @return masked string
     */
    public static String mask(String str, int visibleChars, char maskCharacter) {
        if (isEmpty(str) || str.length() <= visibleChars * 2) {
            return str;
        }
        String start = str.substring(0, visibleChars);
        String end = str.substring(str.length() - visibleChars);
        int maskLength = str.length() - (visibleChars * 2);
        String mask = String.valueOf(maskCharacter).repeat(maskLength);
        return start + mask + end;
    }

    /**
     * Masks an email address
     *
     * @param email the email to mask
     * @return masked email (e.g., "jo***@ex***.com")
     */
    public static String maskEmail(String email) {
        if (!isValidEmail(email)) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        String maskedLocal = localPart.length() >= 2
                ? localPart.substring(0, 2) + "***"
                : localPart + "***";
        
        String[] domainParts = domain.split("\\.");
        String domainName = domainParts[0];
        String maskedDomainName = domainName.length() >= 2
                ? domainName.substring(0, 2) + "***"
                : domainName + "***";
        
        // Reconstruct domain with remaining parts
        StringBuilder maskedDomain = new StringBuilder(maskedDomainName);
        for (int i = 1; i < domainParts.length; i++) {
            maskedDomain.append(".").append(domainParts[i]);
        }
        
        return maskedLocal + "@" + maskedDomain;
    }

    /**
     * Joins an array of strings with a delimiter
     *
     * @param delimiter the delimiter
     * @param elements  the elements to join
     * @return joined string
     */
    public static String join(String delimiter, String... elements) {
        if (elements == null || elements.length == 0) {
            return "";
        }
        return Arrays.stream(elements)
                .filter(StringUtils::isNotNull)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * Joins a list of strings with a delimiter
     *
     * @param delimiter the delimiter
     * @param elements  the list of elements to join
     * @return joined string
     */
    public static String join(String delimiter, List<String> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }
        return elements.stream()
                .filter(StringUtils::isNotNull)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * Removes all whitespace from a string
     *
     * @param str the string
     * @return string without whitespace
     */
    public static String removeWhitespace(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.replaceAll("\\s+", "");
    }

    /**
     * Checks if a string contains only digits
     *
     * @param str the string to check
     * @return true if string contains only digits
     */
    public static boolean isNumeric(String str) {
        if (isEmpty(str)) {
            return false;
        }
        return str.matches("\\d+");
    }

    /**
     * Checks if a string contains only alphabetic characters
     *
     * @param str the string to check
     * @return true if string contains only letters
     */
    public static boolean isAlphabetic(String str) {
        if (isEmpty(str)) {
            return false;
        }
        return str.matches("[a-zA-Z]+");
    }

    /**
     * Checks if a string contains only alphanumeric characters
     *
     * @param str the string to check
     * @return true if string contains only letters and digits
     */
    public static boolean isAlphanumeric(String str) {
        if (isEmpty(str)) {
            return false;
        }
        return str.matches("[a-zA-Z0-9]+");
    }

    private static boolean isNotNull(String str) {
        return str != null;
    }
}



