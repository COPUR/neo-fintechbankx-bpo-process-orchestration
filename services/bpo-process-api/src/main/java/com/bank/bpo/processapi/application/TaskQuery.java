package com.bank.bpo.processapi.application;

import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.QUERY_NOT_ALLOWED;

import com.bank.bpo.processapi.domain.TaskRefusal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * One page request for a task list: which tasks ({@code mine}, {@code open} for unassigned, {@code all}), how
 * many, where the last page ended and, for the new-task check, only tasks created after a time. Every value is
 * checked here; anything malformed is QUERY_NOT_ALLOWED.
 */
public record TaskQuery(Filter filter, int limit, Position after, Instant createdAfter) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,3}");
    private static final Pattern CURSOR = Pattern.compile("[A-Za-z0-9_-]{1,96}");
    private static final Pattern POSITION = Pattern.compile("(-|[0-9]{1,19})\\.(-|[0-9]{1,19})\\.([0-9]{1,19})");

    public enum Filter {
        MINE, OPEN, ALL;

        boolean keeps(TaskView v) {
            return switch (this) {
                case MINE -> v.assignment() == TaskView.Assignment.MINE;
                case OPEN -> v.assignment() == TaskView.Assignment.UNASSIGNED;
                case ALL -> true;
            };
        }
    }

    /** Where a page ended, in list order: due date, creation date (missing ones last), then task key. */
    public record Position(Instant due, Instant created, long key) {

        static Position of(TaskView v) {
            return new Position(v.dueAt(), v.createdAt(), v.taskKey());
        }

        /** The opaque cursor the client hands back; it carries no user data and grants nothing. */
        String encode() {
            String plain = millis(due) + "." + millis(created) + "." + key;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.US_ASCII));
        }

        static Position decode(String cursor) {
            if (!CURSOR.matcher(cursor).matches()) {
                throw new TaskRefusal(QUERY_NOT_ALLOWED);
            }
            try {
                var m = POSITION.matcher(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
                if (!m.matches()) {
                    throw new TaskRefusal(QUERY_NOT_ALLOWED);
                }
                return new Position(instant(m.group(1)), instant(m.group(2)), Long.parseLong(m.group(3)));
            } catch (IllegalArgumentException e) {
                throw new TaskRefusal(QUERY_NOT_ALLOWED);
            }
        }

        private static String millis(Instant t) {
            return t == null ? "-" : Long.toString(t.toEpochMilli());
        }

        private static Instant instant(String s) {
            return s.equals("-") ? null : Instant.ofEpochMilli(Long.parseLong(s));
        }
    }

    /** Reads the query parameters as sent; a missing one takes its default. */
    public static TaskQuery of(String filter, String limit, String cursor, String createdAfter) {
        Filter f = switch (filter == null ? "all" : filter) {
            case "mine" -> Filter.MINE;
            case "open" -> Filter.OPEN;
            case "all" -> Filter.ALL;
            default -> throw new TaskRefusal(QUERY_NOT_ALLOWED);
        };
        int n = DEFAULT_LIMIT;
        if (limit != null) {
            if (!NUMBER.matcher(limit).matches()) {
                throw new TaskRefusal(QUERY_NOT_ALLOWED);
            }
            n = Integer.parseInt(limit);
            if (n < 1 || n > MAX_LIMIT) {
                throw new TaskRefusal(QUERY_NOT_ALLOWED);
            }
        }
        Instant since = null;
        if (createdAfter != null) {
            try {
                since = Instant.parse(createdAfter);
            } catch (DateTimeParseException e) {
                throw new TaskRefusal(QUERY_NOT_ALLOWED);
            }
        }
        return new TaskQuery(f, n, cursor == null ? null : Position.decode(cursor), since);
    }
}
