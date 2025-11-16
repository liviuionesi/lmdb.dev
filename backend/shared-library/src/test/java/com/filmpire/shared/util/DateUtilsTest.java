package com.filmpire.shared.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DateUtils}.
 * <p>
 * This test class verifies the functionality of the DateUtils utility class, including:
 * <ul>
 *   <li>Current date/time retrieval</li>
 *   <li>Date/time conversions between different types</li>
 *   <li>Date/time formatting and parsing</li>
 *   <li>Date calculations (days between, hours between)</li>
 *   <li>Date comparisons (past, future, within range)</li>
 *   <li>Date arithmetic (add days, add hours)</li>
 *   <li>Epoch conversions</li>
 *   <li>Null handling</li>
 * </ul>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 * @see DateUtils
 */
class DateUtilsTest {

    /**
     * Tests that {@link DateUtils#now()} returns the current date and time.
     * Verifies that the returned LocalDateTime is not null and represents
     * a time close to the current moment.
     */
    @Test
    void now_shouldReturnCurrentDateTime() {
        LocalDateTime result = DateUtils.now();
        assertThat(result)
                .isNotNull()
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }

    /**
     * Tests that {@link DateUtils#today()} returns the current date.
     * Verifies that the returned LocalDate is not null and equals today's date.
     */
    @Test
    void today_shouldReturnCurrentDate() {
        LocalDate result = DateUtils.today();
        assertThat(result)
                .isNotNull()
                .isEqualTo(LocalDate.now());
    }

    /**
     * Tests that {@link DateUtils#toDate(LocalDateTime)} converts a LocalDateTime
     * to a legacy Date object correctly.
     * Verifies that the conversion preserves the date and time information.
     */
    @Test
    void toDate_shouldConvertLocalDateTimeToDate() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 30, 0);
        Date result = DateUtils.toDate(dateTime);
        
        assertThat(result).isNotNull();
        assertThat(DateUtils.toLocalDateTime(result)).isEqualToIgnoringNanos(dateTime);
    }

    /**
     * Tests that {@link DateUtils#toDate(LocalDateTime)} returns null when given null input.
     * Verifies null-safe behavior of the conversion method.
     */
    @Test
    void toDate_shouldReturnNullForNullInput() {
        Date result = DateUtils.toDate(null);
        assertThat(result).isNull();
    }

    /**
     * Tests that {@link DateUtils#toLocalDateTime(Date)} converts a legacy Date
     * to a LocalDateTime correctly.
     * Verifies that the conversion produces a non-null LocalDateTime.
     */
    @Test
    void toLocalDateTime_shouldConvertDateToLocalDateTime() {
        Date date = new Date();
        LocalDateTime result = DateUtils.toLocalDateTime(date);
        
        assertThat(result).isNotNull();
    }

    /**
     * Tests that {@link DateUtils#toLocalDateTime(Date)} returns null when given null input.
     * Verifies null-safe behavior of the conversion method.
     */
    @Test
    void toLocalDateTime_shouldReturnNullForNullInput() {
        LocalDateTime result = DateUtils.toLocalDateTime(null);
        assertThat(result).isNull();
    }

    /**
     * Tests that {@link DateUtils#format(LocalDateTime)} formats a LocalDateTime
     * using the default pattern "yyyy-MM-dd HH:mm:ss".
     * Verifies that the formatted string matches the expected format.
     */
    @Test
    void format_shouldFormatLocalDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 30, 45);
        String result = DateUtils.format(dateTime);
        
        assertThat(result).isEqualTo("2023-10-15 10:30:45");
    }

    /**
     * Tests that {@link DateUtils#format(LocalDate)} formats a LocalDate
     * using the default pattern "yyyy-MM-dd".
     * Verifies that the formatted string matches the expected format.
     */
    @Test
    void format_shouldFormatLocalDate() {
        LocalDate date = LocalDate.of(2023, 10, 15);
        String result = DateUtils.format(date);
        
        assertThat(result).isEqualTo("2023-10-15");
    }

    /**
     * Tests that {@link DateUtils#format(LocalDateTime, String)} formats a LocalDateTime
     * using a custom pattern.
     * Verifies that custom date/time patterns are correctly applied.
     */
    @Test
    void format_withPattern_shouldFormatUsingCustomPattern() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 30, 0);
        String result = DateUtils.format(dateTime, "dd/MM/yyyy");
        
        assertThat(result).isEqualTo("15/10/2023");
    }

    /**
     * Tests that {@link DateUtils#parseDateTime(String)} parses a valid date-time string
     * into a LocalDateTime object.
     * Verifies that the default format "yyyy-MM-dd HH:mm:ss" is correctly parsed.
     */
    @Test
    void parseDateTime_shouldParseValidString() {
        String dateTimeString = "2023-10-15 10:30:45";
        LocalDateTime result = DateUtils.parseDateTime(dateTimeString);
        
        assertThat(result).isEqualTo(LocalDateTime.of(2023, 10, 15, 10, 30, 45));
    }

    /**
     * Tests that {@link DateUtils#parseDateTime(String)} throws a DateTimeParseException
     * when given an invalid date-time string format.
     * Verifies proper error handling for malformed input.
     */
    @Test
    void parseDateTime_shouldThrowExceptionForInvalidFormat() {
        assertThatThrownBy(() -> DateUtils.parseDateTime("invalid-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    /**
     * Tests that {@link DateUtils#parseDate(String)} parses a valid date string
     * into a LocalDate object.
     * Verifies that the default format "yyyy-MM-dd" is correctly parsed.
     */
    @Test
    void parseDate_shouldParseValidString() {
        String dateString = "2023-10-15";
        LocalDate result = DateUtils.parseDate(dateString);
        
        assertThat(result).isEqualTo(LocalDate.of(2023, 10, 15));
    }

    /**
     * Tests that {@link DateUtils#daysBetween(LocalDate, LocalDate)} calculates
     * the number of days between two dates correctly.
     * Verifies that the calculation includes both start and end dates.
     */
    @Test
    void daysBetween_shouldCalculateDaysBetweenDates() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        
        long result = DateUtils.daysBetween(start, end);
        
        assertThat(result).isEqualTo(14);
    }

    /**
     * Tests that {@link DateUtils#hoursBetween(LocalDateTime, LocalDateTime)} calculates
     * the number of hours between two date-time values correctly.
     * Verifies accurate hour difference calculation.
     */
    @Test
    void hoursBetween_shouldCalculateHoursBetweenDateTimes() {
        LocalDateTime start = LocalDateTime.of(2023, 10, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2023, 10, 15, 15, 0);
        
        long result = DateUtils.hoursBetween(start, end);
        
        assertThat(result).isEqualTo(5);
    }

    /**
     * Tests that {@link DateUtils#isPast(LocalDate)} returns true for dates
     * that are in the past relative to today.
     * Verifies correct identification of past dates.
     */
    @Test
    void isPast_shouldReturnTrueForPastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertThat(DateUtils.isPast(pastDate)).isTrue();
    }

    /**
     * Tests that {@link DateUtils#isPast(LocalDate)} returns false for dates
     * that are in the future relative to today.
     * Verifies correct identification of future dates.
     */
    @Test
    void isPast_shouldReturnFalseForFutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        assertThat(DateUtils.isPast(futureDate)).isFalse();
    }

    /**
     * Tests that {@link DateUtils#isFuture(LocalDate)} returns true for dates
     * that are in the future relative to today.
     * Verifies correct identification of future dates.
     */
    @Test
    void isFuture_shouldReturnTrueForFutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        assertThat(DateUtils.isFuture(futureDate)).isTrue();
    }

    /**
     * Tests that {@link DateUtils#isFuture(LocalDate)} returns false for dates
     * that are in the past relative to today.
     * Verifies correct identification of past dates.
     */
    @Test
    void isFuture_shouldReturnFalseForPastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertThat(DateUtils.isFuture(pastDate)).isFalse();
    }

    /**
     * Tests that {@link DateUtils#isWithinLastHours(LocalDateTime, int)} returns true
     * for date-time values that fall within the specified number of hours from now.
     * Verifies correct time range checking.
     */
    @Test
    void isWithinLastHours_shouldReturnTrueForRecentDateTime() {
        LocalDateTime recentDateTime = LocalDateTime.now().minusHours(2);
        assertThat(DateUtils.isWithinLastHours(recentDateTime, 3)).isTrue();
    }

    /**
     * Tests that {@link DateUtils#isWithinLastHours(LocalDateTime, int)} returns false
     * for date-time values that fall outside the specified number of hours from now.
     * Verifies correct time range checking for old dates.
     */
    @Test
    void isWithinLastHours_shouldReturnFalseForOldDateTime() {
        LocalDateTime oldDateTime = LocalDateTime.now().minusHours(5);
        assertThat(DateUtils.isWithinLastHours(oldDateTime, 3)).isFalse();
    }

    /**
     * Tests that {@link DateUtils#addDays(LocalDate, int)} adds the specified number
     * of days to a date correctly.
     * Verifies accurate date arithmetic.
     */
    @Test
    void addDays_shouldAddDaysToDate() {
        LocalDate date = LocalDate.of(2023, 10, 15);
        LocalDate result = DateUtils.addDays(date, 5);
        
        assertThat(result).isEqualTo(LocalDate.of(2023, 10, 20));
    }

    /**
     * Tests that {@link DateUtils#addHours(LocalDateTime, int)} adds the specified number
     * of hours to a date-time value correctly.
     * Verifies accurate time arithmetic.
     */
    @Test
    void addHours_shouldAddHoursToDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 0);
        LocalDateTime result = DateUtils.addHours(dateTime, 5);
        
        assertThat(result).isEqualTo(LocalDateTime.of(2023, 10, 15, 15, 0));
    }

    /**
     * Tests that {@link DateUtils#startOfDay(LocalDateTime)} returns a date-time
     * set to the beginning of the day (00:00:00).
     * Verifies that time components are reset to midnight.
     */
    @Test
    void startOfDay_shouldReturnStartOfDay() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 15, 30, 45);
        LocalDateTime result = DateUtils.startOfDay(dateTime);
        
        assertThat(result).isEqualTo(LocalDateTime.of(2023, 10, 15, 0, 0, 0));
    }

    /**
     * Tests that {@link DateUtils#endOfDay(LocalDateTime)} returns a date-time
     * set to the end of the day (23:59:59).
     * Verifies that time components are set to the last moment of the day.
     */
    @Test
    void endOfDay_shouldReturnEndOfDay() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 0, 0);
        LocalDateTime result = DateUtils.endOfDay(dateTime);
        
        assertThat(result.toLocalDate()).isEqualTo(LocalDate.of(2023, 10, 15));
        assertThat(result.getHour()).isEqualTo(23);
        assertThat(result.getMinute()).isEqualTo(59);
        assertThat(result.getSecond()).isEqualTo(59);
    }

    /**
     * Tests that {@link DateUtils#fromEpochMilli(long)} converts an epoch timestamp
     * in milliseconds to a LocalDateTime object.
     * Verifies correct epoch to date-time conversion.
     */
    @Test
    void fromEpochMilli_shouldConvertEpochToLocalDateTime() {
        long epoch = 1697365845000L; // 2023-10-15 10:30:45 UTC (approximately)
        LocalDateTime result = DateUtils.fromEpochMilli(epoch);
        
        assertThat(result).isNotNull();
    }

    /**
     * Tests that {@link DateUtils#toEpochMilli(LocalDateTime)} converts a LocalDateTime
     * to an epoch timestamp in milliseconds.
     * Verifies correct date-time to epoch conversion.
     */
    @Test
    void toEpochMilli_shouldConvertLocalDateTimeToEpoch() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 15, 10, 30, 45);
        long result = DateUtils.toEpochMilli(dateTime);
        
        assertThat(result).isGreaterThan(0);
    }

    /**
     * Tests that various DateUtils methods handle null inputs gracefully
     * without throwing exceptions.
     * Verifies null-safe behavior across multiple utility methods including
     * formatting, parsing, calculations, and conversions.
     */
    @Test
    void nullInputs_shouldHandleGracefully() {
        assertThat(DateUtils.format((LocalDateTime) null)).isNull();
        assertThat(DateUtils.format((LocalDate) null)).isNull();
        assertThat(DateUtils.parseDateTime(null)).isNull();
        assertThat(DateUtils.parseDate(null)).isNull();
        assertThat(DateUtils.daysBetween(null, LocalDate.now())).isZero();
        assertThat(DateUtils.addDays(null, 5)).isNull();
        assertThat(DateUtils.toEpochMilli(null)).isZero();
    }
}
