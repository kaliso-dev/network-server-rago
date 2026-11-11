package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class StatsPeriodTest {

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    }

    @Test
    void daysAndWeeksAreThoseOfParis() {
        assertEquals("2026-11-08", StatsPeriod.dayKey(at(2026, 11, 8, 0, 30)));
        assertEquals("2026-11-07", StatsPeriod.dayKey(at(2026, 11, 7, 23, 59)));
        assertEquals("2026-W45", StatsPeriod.weekKey(at(2026, 11, 8, 23, 59)), "Sunday ends the ISO week");
        assertEquals("2026-W46", StatsPeriod.weekKey(at(2026, 11, 9, 0, 0)), "Monday starts it");
        assertEquals("2026-W01", StatsPeriod.weekKey(at(2026, 1, 1, 12, 0)));
    }

    @Test
    void saysWhenThePeriodStartsAgain() {
        long evening = at(2026, 11, 8, 22, 30);
        assertEquals("Resets in 2 h", StatsPeriod.DAY.resetText(evening));
        assertEquals("Resets in 2 h", StatsPeriod.WEEK.resetText(evening));
        assertEquals("Resets in 15 min", StatsPeriod.DAY.resetText(at(2026, 11, 8, 23, 45)));
        assertEquals("Resets in 7 days", StatsPeriod.WEEK.resetText(at(2026, 11, 9, 0, 0)));
        assertEquals("Never resets", StatsPeriod.ALL.resetText(evening));
        assertEquals(StatsPeriod.WEEK, StatsPeriod.DAY.next());
        assertEquals(StatsPeriod.DAY, StatsPeriod.ALL.next());
    }
}
