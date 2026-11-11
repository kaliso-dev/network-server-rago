package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A rank offered: YES or NO in a book, within a minute; PREMIUM and above only. */
class GiftRequestsTest {

    private static final UUID GIVER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Test
    void anOfferIsAnsweredOnceWithinAMinute() {
        GiftRequests gifts = new GiftRequests();
        gifts.offer(TARGET, GIVER, "vip", 1000);
        assertNull(gifts.take(GIVER, TARGET, 1000), "only the one offered answers");
        assertEquals("vip", gifts.take(TARGET, GIVER, 1000 + GiftRequests.EXPIRY_MS));
        assertNull(gifts.take(TARGET, GIVER, 2000), "once");
        gifts.offer(TARGET, GIVER, "vip", 1000);
        gifts.offer(TARGET, GIVER, "premium", 5000);
        assertEquals("premium", gifts.take(TARGET, GIVER, 6000), "the last offer stands");
        gifts.offer(TARGET, GIVER, "vip", 1000);
        assertNull(gifts.take(TARGET, GIVER, 1001 + GiftRequests.EXPIRY_MS), "too late");
        gifts.offer(TARGET, GIVER, "vip", 1000);
        gifts.forget(GIVER);
        assertNull(gifts.take(TARGET, GIVER, 1000), "the giver left");
    }

    @Test
    void theBookSaysWhoGivesWhatWithYesAndNo() {
        String page = GiftRequests.page("§d[PREMIUM§f+§d] §dqRago", "qRago", "§b[VIP]");
        JsonArray parts = new JsonParser().parse(page).getAsJsonArray();
        StringBuilder text = new StringBuilder();
        List<String> commands = new ArrayList<>();
        for (JsonElement part : parts) {
            if (part.isJsonObject()) {
                JsonObject object = part.getAsJsonObject();
                text.append(object.get("text").getAsString());
                if (object.has("clickEvent")) {
                    commands.add(object.getAsJsonObject("clickEvent").get("value").getAsString());
                }
            }
        }
        String plain = text.toString().replaceAll("§.", "");
        assertTrue(plain.contains("[PREMIUM+] qRago\nwants to give you\n[VIP]!"), plain);
        assertTrue(plain.contains("YES") && plain.contains("NO"), plain);
        assertEquals(2, commands.size());
        assertEquals("/gift accept qRago", commands.get(0));
        assertEquals("/gift deny qRago", commands.get(1));
        assertTrue(!text.toString().contains("§f"), "no white on the paper");
        assertEquals(4, new JsonParser().parse(GiftRequests.buttons("qRago")).getAsJsonArray().size(), "\"\", YES, a space, NO");
    }

    @Test
    void premiumGiftsTheOthersDoNot() {
        assertTrue(Ranks.premium().permissions().contains(GiftRequests.PERMISSION));
        assertTrue(!Ranks.vip().permissions().contains(GiftRequests.PERMISSION));
        assertEquals("\\\"a\\\\b\\n", GiftRequests.escape("\"a\\b\n"));
    }
}
