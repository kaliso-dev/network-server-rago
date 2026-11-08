package gg.rago.lobby;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * The periods of the stats and leaderboards, in the time of Paris: the day starts at midnight, the
 * week on Monday at midnight.
 */
enum StatsPeriod {

    DAY("Daily"),
    WEEK("Weekly"),
    ALL("Lifetime");

    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final String label;

    StatsPeriod(String label) {
        this.label = label;
    }

    /** "Daily", "Weekly", "Lifetime". */
    String label() {
        return label;
    }

    /** The next period, for a click on a leaderboard: day, week, ever, day... */
    StatsPeriod next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** 2026-11-08. */
    static String dayKey(long millis) {
        return date(millis).toString();
    }

    /** 2026-W45, the ISO week. */
    static String weekKey(long millis) {
        LocalDate date = date(millis);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + (week < 10 ? "0" : "") + week;
    }

    /** Milliseconds until this period starts again; -1 for {@link #ALL}, which never does. */
    long untilReset(long millis) {
        ZonedDateTime now = Instant.ofEpochMilli(millis).atZone(ZONE);
        ZonedDateTime next;
        switch (this) {
            case DAY:
                next = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
                break;
            case WEEK:
                next = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(ZONE);
                break;
            default:
                return -1L;
        }
        return next.toInstant().toEpochMilli() - millis;
    }

    /** "Resets in 5 h", "Resets in 12 min", or "Never resets". */
    String resetText(long millis) {
        long left = untilReset(millis);
        if (left < 0) {
            return "Never resets";
        }
        long minutes = Math.max(1L, (left + 59_999L) / 60_000L);
        if (minutes >= 48 * 60) {
            return "Resets in " + (minutes + 24 * 60 - 1) / (24 * 60) + " days";
        }
        if (minutes >= 60) {
            return "Resets in " + (minutes + 59) / 60 + " h";
        }
        return "Resets in " + minutes + " min";
    }

    private static LocalDate date(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
    }
}
