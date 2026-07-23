package com.filmpire.shared.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date and time operations.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
public final class DateUtils {

    private DateUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Gets the current LocalDateTime
     *
     * @return current LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Gets the current LocalDate
     *
     * @return current LocalDate
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Converts LocalDateTime to Date
     *
     * @param localDateTime the LocalDateTime to convert
     * @return Date object
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Converts Date to LocalDateTime
     *
     * @param date the Date to convert
     * @return LocalDateTime object
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Formats LocalDateTime to string using default format
     *
     * @param dateTime the LocalDateTime to format
     * @return formatted string
     */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DEFAULT_DATE_TIME_FORMATTER);
    }

    /**
     * Formats LocalDate to string using default format
     *
     * @param date the LocalDate to format
     * @return formatted string
     */
    public static String format(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DEFAULT_DATE_FORMATTER);
    }

    /**
     * Formats LocalDateTime to string using custom pattern
     *
     * @param dateTime the LocalDateTime to format
     * @param pattern  the pattern to use
     * @return formatted string
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null || pattern == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Parses string to LocalDateTime using default format
     *
     * @param dateTimeString the string to parse
     * @return LocalDateTime object
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, DEFAULT_DATE_TIME_FORMATTER);
    }

    /**
     * Parses string to LocalDate using default format
     *
     * @param dateString the string to parse
     * @return LocalDate object
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateString, DEFAULT_DATE_FORMATTER);
    }

    /**
     * Parses ISO 8601 formatted string to LocalDateTime
     *
     * @param isoString the ISO string to parse
     * @return LocalDateTime object
     */
    public static LocalDateTime parseIsoDateTime(String isoString) {
        if (isoString == null || isoString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(isoString, ISO_DATE_TIME_FORMATTER);
    }

    /**
     * Calculates the difference in days between two dates
     *
     * @param start the start date
     * @param end   the end date
     * @return number of days between start and end
     */
    public static long daysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Calculates the elapsed hours between two date-times.
     *
     * <p>The arguments are resolved against {@link ZoneId#systemDefault()}
     * before the difference is taken (the same convention {@link #toDate} uses).
     * This matters across a daylight-saving transition: counting on bare
     * {@link LocalDateTime} compares wall-clock readings, so a span crossing a
     * DST boundary is off by the shift — 01:00 to 03:00 on a spring-forward day
     * is one elapsed hour, not two. Resolving to a zone first yields real
     * elapsed time.</p>
     *
     * @param start the start date-time
     * @param end   the end date-time
     * @return number of elapsed hours between start and end
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        ZoneId zone = ZoneId.systemDefault();
        return ChronoUnit.HOURS.between(start.atZone(zone), end.atZone(zone));
    }

    /**
     * Checks if a date is in the past
     *
     * @param date the date to check
     * @return true if the date is before today
     */
    public static boolean isPast(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }

    /**
     * Checks if a date is in the future
     *
     * @param date the date to check
     * @return true if the date is after today
     */
    public static boolean isFuture(LocalDate date) {
        return date != null && date.isAfter(LocalDate.now());
    }

    /**
     * Checks if a date-time is within the last N hours
     *
     * @param dateTime the date-time to check
     * @param hours    number of hours
     * @return true if the date-time is within the last N hours
     */
    public static boolean isWithinLastHours(LocalDateTime dateTime, long hours) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return dateTime.isAfter(threshold);
    }

    /**
     * Adds days to a date
     *
     * @param date the date
     * @param days number of days to add
     * @return new LocalDate with days added
     */
    public static LocalDate addDays(LocalDate date, long days) {
        if (date == null) {
            return null;
        }
        return date.plusDays(days);
    }

    /**
     * Adds hours to a date-time
     *
     * @param dateTime the date-time
     * @param hours    number of hours to add
     * @return new LocalDateTime with hours added
     */
    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.plusHours(hours);
    }

    /**
     * Gets the start of the day for a given date-time
     *
     * @param dateTime the date-time
     * @return LocalDateTime at 00:00:00
     */
    public static LocalDateTime startOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Gets the end of the day for a given date-time
     *
     * @param dateTime the date-time
     * @return LocalDateTime at 23:59:59.999999999
     */
    public static LocalDateTime endOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1);
    }

    /**
     * Converts epoch milliseconds to LocalDateTime
     *
     * @param epochMilli epoch milliseconds
     * @return LocalDateTime object
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /**
     * Converts LocalDateTime to epoch milliseconds
     *
     * @param dateTime the LocalDateTime
     * @return epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

