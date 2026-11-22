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
