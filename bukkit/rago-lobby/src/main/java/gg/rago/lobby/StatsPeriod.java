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

    DAY("du jour"),
    WEEK("de la semaine"),
    ALL("à vie");

    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final String label;

    StatsPeriod(String label) {
        this.label = label;
    }

    /** "du jour", "de la semaine", "à vie". */
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

    /** "Remise à zéro dans 5 h", "dans 12 min", or "Jamais remis à zéro". */
    String resetText(long millis) {
        long left = untilReset(millis);
        if (left < 0) {
            return "Jamais remis à zéro";
        }
        long minutes = Math.max(1L, (left + 59_999L) / 60_000L);
        if (minutes >= 48 * 60) {
            return "Remise à zéro dans " + (minutes + 24 * 60 - 1) / (24 * 60) + " jours";
        }
        if (minutes >= 60) {
            return "Remise à zéro dans " + (minutes + 59) / 60 + " h";
        }
        return "Remise à zéro dans " + minutes + " min";
    }

    private static LocalDate date(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
    }
}
