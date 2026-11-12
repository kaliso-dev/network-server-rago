package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** No ranked queue for a while after leaving a ranked game. */
class RankedBansTest {

    private static final long MINUTE = 60_000L;

    @Test
    void theRankedQueuesCloseForTheMinutesAfterAForfeit() {
        RankedBans bans = new RankedBans(5);
        UUID leaver = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(0, bans.left(leaver, 0), "nothing before");
        bans.forfeited(leaver, 1_000);
        assertEquals(5 * MINUTE, bans.left(leaver, 1_000));
        assertEquals(5 * MINUTE - 28_000, bans.left(leaver, 29_000));
        assertEquals(0, bans.left(other, 29_000), "only for the one who left");
        assertEquals(0, bans.left(leaver, 1_000 + 5 * MINUTE), "open again after 5 minutes");
        assertEquals(0, bans.left(leaver, 1_000), "and forgotten");
    }

    @Test
    void anotherForfeitStartsTheMinutesAgain() {
        RankedBans bans = new RankedBans(5);
        UUID leaver = UUID.randomUUID();
        bans.forfeited(leaver, 0);
        bans.forfeited(leaver, 4 * MINUTE);
        assertEquals(5 * MINUTE, bans.left(leaver, 4 * MINUTE));
    }

    @Test
    void zeroMinutesNeverCloseThem() {
        RankedBans bans = new RankedBans(0);
        UUID leaver = UUID.randomUUID();
        bans.forfeited(leaver, 0);
        assertEquals(0, bans.left(leaver, 0));
        assertEquals(0, bans.minutes());
        assertEquals(RankedBans.MAX_MINUTES, new RankedBans(500).minutes(), "an hour at most");
        assertEquals(0, new RankedBans(-3).minutes());
    }

    @Test
    void theTimeLeftIsRoundedUp() {
        assertEquals("5:00", RankedBans.clock(5 * MINUTE));
        assertEquals("4:32", RankedBans.clock(4 * MINUTE + 31_200));
        assertEquals("0:01", RankedBans.clock(1));
    }

    @Test
    void fiveMinutesByDefault() throws Exception {
        YamlConfiguration bundled;
        try (Reader reader = new InputStreamReader(RankedBansTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        assertEquals(5, bundled.getInt("file.abandon-classe-minutes", -1));
    }
}
