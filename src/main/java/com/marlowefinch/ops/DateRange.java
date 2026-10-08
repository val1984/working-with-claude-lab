package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today"; a blank parameter counts as
 * missing. {@link #resolve} checks the request (TODO-232): each bound that is given must be
 * an ISO date, {@code from} must not be after {@code to}, and {@code to} may be at most
 * {@value #MAX_SPAN_DAYS} days after {@code from}. The constructor itself does not check,
 * so a backwards range can still be built directly.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_SPAN_DAYS = 366;

    /** Resolves and validates the range, throwing {@link InvalidParametersException} on bad input. */
    public static DateRange resolve(String from, String to, Clock clock) {
        List<String> errors = new ArrayList<>();
        DateRange range = resolve(from, to, clock, errors);
        if (!errors.isEmpty()) {
            throw new InvalidParametersException(errors);
        }
        return range;
    }

    /** Resolves the range, adding any problems to {@code errors}; returns null if there were any. */
    public static DateRange resolve(String from, String to, Clock clock, List<String> errors) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = isMissing(to) ? today : parse("to", to, errors);
        LocalDate start = isMissing(from) ? today.minusDays(DEFAULT_DAYS) : parse("from", from, errors);
        if (start == null || end == null) {
            return null;
        }
        if (start.isAfter(end)) {
            errors.add("from must be on or before to");
            return null;
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
            errors.add("the range may span at most " + MAX_SPAN_DAYS + " days");
            return null;
        }
        return new DateRange(start, end);
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate parse(String name, String value, List<String> errors) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(name + " must be an ISO date (YYYY-MM-DD)");
            return null;
        }
    }
}
