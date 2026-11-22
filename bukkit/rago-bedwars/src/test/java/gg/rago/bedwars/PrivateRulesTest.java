package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrivateRulesTest {

    @Test
    void theSettingsTheOwnerAskedForFirst() {
        List<String> ids = Arrays.asList(PrivateRules.SPEED, PrivateRules.MAX_DIAMOND, PrivateRules.MAX_EMERALD,
                PrivateRules.BREAK_MAP);
        for (int i = 0; i < ids.size(); i++) {
            assertEquals(ids.get(i), PrivateRules.SETTINGS.get(i).id, "in the order of the menu");
        }
        assertEquals(Arrays.asList("None", "Speed I", "Speed II", "Speed III"), PrivateRules.SETTINGS.get(0).choices);
        assertEquals(Arrays.asList("Off", "On"), PrivateRules.SETTINGS.get(1).choices);
    }

    @Test
    void withoutChoicesTheGameIsNormal() {
        assertSame(PrivateRules.NONE, PrivateRules.of(null));
        assertSame(PrivateRules.NONE, PrivateRules.of(Collections.<String, Integer>emptyMap()));
        assertFalse(PrivateRules.NONE.any());
        int[] times = {360, 720, 1080, 1440, 1800, 2700};
        assertArrayEquals(times, PrivateRules.NONE.times(times));
    }

    @Test
    void readsTheChoicesOfTheLeader() {
        Map<String, Integer> chosen = new HashMap<>();
        chosen.put(PrivateRules.SPEED, 2);
        chosen.put(PrivateRules.MAX_DIAMOND, 1);
        chosen.put(PrivateRules.MAX_EMERALD, 1);
        chosen.put(PrivateRules.BREAK_MAP, 0);
        PrivateRules rules = PrivateRules.of(chosen);
        assertEquals(2, rules.speed());
        assertTrue(rules.maxDiamond());
        assertTrue(rules.maxEmerald());
        assertFalse(rules.breakMap());
        assertTrue(rules.any());
        assertEquals(Arrays.asList("Speed II", "Max Diamond", "Max Emerald"), rules.summary());
    }

    @Test
    void maxEmeraldKeepsTheLengthOfTheGame() {
        Map<String, Integer> chosen = Collections.singletonMap(PrivateRules.MAX_EMERALD, 1);
        int[] times = {360, 720, 1080, 1440, 1800, 2700};
        assertArrayEquals(new int[] {360, -1, 1080, -1, 1800, 2700}, PrivateRules.of(chosen).times(times),
                "no Emerald II nor III, the generators being there; the beds and the end as usual");
        assertEquals(720, times[1], "the times of the mode untouched");
    }

    @Test
    void thenThoseOfHypixelAsItsMenuTellsThem() {
        List<String> ids = Arrays.asList(PrivateRules.HEALTH_BUFF, PrivateRules.LOW_GRAVITY, PrivateRules.RESPAWN_TIME,
                PrivateRules.EVENT_TIMES, PrivateRules.BED_INSTABREAK);
        assertEquals(4 + ids.size(), PrivateRules.SETTINGS.size());
        for (int i = 0; i < ids.size(); i++) {
            assertEquals(ids.get(i), PrivateRules.SETTINGS.get(4 + i).id, "after the owner's four, in the order of Hypixel's menu");
        }
        assertEquals(Arrays.asList("Normal Health", "Double Health", "Triple Health"), PrivateRules.SETTINGS.get(4).choices);
        assertEquals(Arrays.asList("Normal", "1 second", "10 seconds"), PrivateRules.SETTINGS.get(6).choices);
        assertEquals(Arrays.asList("1x - Normal", "0.5x - Slower", "2x - Faster", "4x - Fastest"),
                PrivateRules.SETTINGS.get(7).choices);
    }

    @Test
    void healthBuffDoublesOrTriplesTheHearts() {
        assertEquals(-1, PrivateRules.NONE.healthBoost(), "no Health Boost in a normal game");
        // each level of Health Boost: 4 health points more, over the 20 of a player
        int doubled = PrivateRules.of(Collections.singletonMap(PrivateRules.HEALTH_BUFF, 1)).healthBoost();
        assertEquals(40, 20 + 4 * (doubled + 1), "Double Health: 20 hearts");
        int tripled = PrivateRules.of(Collections.singletonMap(PrivateRules.HEALTH_BUFF, 2)).healthBoost();
        assertEquals(60, 20 + 4 * (tripled + 1), "Triple Health: 30 hearts");
    }

    @Test
    void respawnTimeOrThatOfTheMode() {
        assertEquals(5, PrivateRules.NONE.respawnSeconds(5));
        assertEquals(1, PrivateRules.of(Collections.singletonMap(PrivateRules.RESPAWN_TIME, 1)).respawnSeconds(5));
        assertEquals(10, PrivateRules.of(Collections.singletonMap(PrivateRules.RESPAWN_TIME, 2)).respawnSeconds(5));
    }

    @Test
    void eventTimesBringEachEventSoonerOrLater() {
        int[] times = {360, 720, 1080, 1440, 1800, 2700};
        assertArrayEquals(new int[] {720, 1440, 2160, 2880, 3600, 5400},
                PrivateRules.of(Collections.singletonMap(PrivateRules.EVENT_TIMES, 1)).times(times), "0.5x: slower");
        assertArrayEquals(new int[] {180, 360, 540, 720, 900, 1350},
                PrivateRules.of(Collections.singletonMap(PrivateRules.EVENT_TIMES, 2)).times(times), "2x: faster");
        assertArrayEquals(new int[] {90, 180, 270, 360, 450, 675},
                PrivateRules.of(Collections.singletonMap(PrivateRules.EVENT_TIMES, 3)).times(times), "4x: fastest");
        Map<String, Integer> chosen = new HashMap<>();
        chosen.put(PrivateRules.EVENT_TIMES, 2);
        chosen.put(PrivateRules.MAX_EMERALD, 1);
        assertArrayEquals(new int[] {180, -1, 540, -1, 900, 1350}, PrivateRules.of(chosen).times(times),
                "with Max Emerald, no Emerald II nor III");
        assertArrayEquals(new int[] {-1, 360, 540, 720, 900, 1350},
                PrivateRules.of(Collections.singletonMap(PrivateRules.EVENT_TIMES, 2)).times(new int[] {-1, 720, 1080, 1440, 1800, 2700}),
                "an event that does not come does not come");
    }

    @Test
    void theHypixelSettingsAtTheStart() {
        Map<String, Integer> chosen = new HashMap<>();
        chosen.put(PrivateRules.HEALTH_BUFF, 1);
        chosen.put(PrivateRules.LOW_GRAVITY, 1);
        chosen.put(PrivateRules.RESPAWN_TIME, 1);
        chosen.put(PrivateRules.EVENT_TIMES, 2);
        chosen.put(PrivateRules.BED_INSTABREAK, 1);
        PrivateRules rules = PrivateRules.of(chosen);
        assertTrue(rules.lowGravity());
        assertTrue(rules.bedInstabreak());
        assertFalse(PrivateRules.NONE.lowGravity());
        assertFalse(PrivateRules.NONE.bedInstabreak());
        assertEquals(Arrays.asList("Double Health", "Low Gravity", "Respawn Time: 1 second", "Event Times: 2x - Faster",
                "Bed Instabreak"), rules.summary());
    }

    @Test
    void aChoiceOutOfItsSettingIsThatOfANormalGame() {
        Map<String, Integer> chosen = new HashMap<>();
        chosen.put(PrivateRules.SPEED, 7);
        chosen.put(PrivateRules.BREAK_MAP, -1);
        chosen.put("one-hit-one-kill", 1);
        PrivateRules rules = PrivateRules.of(chosen);
        assertEquals(0, rules.speed());
        assertFalse(rules.breakMap());
        assertFalse(rules.any());
    }
}
