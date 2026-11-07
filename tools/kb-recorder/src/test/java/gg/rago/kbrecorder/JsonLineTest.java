package gg.rago.kbrecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class JsonLineTest {

    @Test
    void writesFieldsInOrderOnOneLine() {
        String line = JsonLine.of("tick").add("tick", 3).add("ground", true).add("name", "Rago").build();
        assertEquals("{\"type\":\"tick\",\"tick\":3,\"ground\":true,\"name\":\"Rago\"}", line);
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        String line = new JsonLine().add("text", "a \"b\" \\ c\nd\u0001").build();
        assertEquals("{\"text\":\"a \\\"b\\\" \\\\ c\\nd\\u0001\"}", line);
    }

    @Test
    void numbersParseBackToTheSameValue() {
        double motion = -0.0784000015258789;
        float yaw = 37.21f;
        String line = new JsonLine().add("my", motion).add("yaw", yaw).build();
        assertEquals("{\"my\":-0.0784000015258789,\"yaw\":37.21}", line);
        assertEquals(motion, Double.parseDouble("-0.0784000015258789"));
        assertEquals(yaw, Float.parseFloat("37.21"));
    }

    @Test
    void nonFiniteNumbersAndNullStringsBecomeNull() {
        String line = new JsonLine().add("a", Double.NaN).add("b", Float.POSITIVE_INFINITY).add("c", (String) null).build();
        assertEquals("{\"a\":null,\"b\":null,\"c\":null}", line);
    }

    @Test
    void nestsObjectsAndArrays() {
        String player = new JsonLine().add("eid", 7).build();
        String line = new JsonLine().raw("players", JsonLine.array(Arrays.asList(player, player))).raw("keys", "{}").build();
        assertEquals("{\"players\":[{\"eid\":7},{\"eid\":7}],\"keys\":{}}", line);
    }
}
