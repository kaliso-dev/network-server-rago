package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HypixelProfileTest {

    /** A quick buy as Hypixel sends it: 21 ids, "null" for an empty slot. */
    private static final String QUICK_BUY = "wool,stone_sword,chainmail_boots,null,bow,speed_ii_potion_(45_seconds),tnt,"
            + "oak_wood_planks,iron_sword,iron_boots,shears,arrow,jump_v_potion_(45_seconds),water_bucket,"
            + "null,null,null,null,null,null,null";

    /** An answer of GET /v2/player, with these Bed Wars stats. */
    private static String answer(String bedwars) {
        return "{\"success\":true,\"player\":{\"uuid\":\"069a79f444e94726a5befca90e38aaf5\",\"displayname\":\"Notch\","
                + "\"stats\":{\"Bedwars\":" + bedwars + ",\"SkyWars\":{\"wins\":1}}}}";
    }

    @Test
    void readsTheQuickBuyAndTheHotbarManager() {
        HypixelProfile profile = HypixelProfile.fromApi(answer("{\"favourites_2\":\"" + QUICK_BUY + "\","
                + "\"favorite_slots\":\"Melee,Blocks,Tools,null,null,null,null,Utility,Compass\",\"wins_bedwars\":3}"),
                "notch", 42L);
        assertEquals("Notch", profile.name(), "the name as Hypixel writes it");
        assertEquals(HypixelProfile.QUICK_BUY, profile.quickBuy().size());
        assertEquals("wool", profile.quickBuy().get(0));
        assertNull(profile.quickBuy().get(3));
        assertEquals("speed_ii_potion_(45_seconds)", profile.quickBuy().get(5));
        assertNull(profile.quickBuy().get(20));
        assertEquals(13, profile.items());
        assertEquals(Arrays.asList("Melee", "Blocks", "Tools", null, null, null, null, "Utility", "Compass"),
                profile.hotbar());
        assertEquals(42L, profile.importedAt());
    }

    @Test
    void doesWithoutAHotbarManager() {
        HypixelProfile profile = HypixelProfile.fromApi(answer("{\"favourites_2\":\"wool,null,tnt\"}"), "Notch", 0L);
        assertNull(profile.hotbar());
        assertEquals(HypixelProfile.QUICK_BUY, profile.quickBuy().size(), "padded to three rows of seven");
        assertEquals("tnt", profile.quickBuy().get(2));
        assertEquals(2, profile.items());
    }

    @Test
    void tellsWhyThereIsNothingToCopy() {
        IllegalArgumentException never = assertThrows(IllegalArgumentException.class,
                () -> HypixelProfile.fromApi("{\"success\":true,\"player\":null}", "Nobody", 0L));
        assertEquals("Nobody has never played on Hypixel.", never.getMessage());
        IllegalArgumentException noShop = assertThrows(IllegalArgumentException.class,
                () -> HypixelProfile.fromApi(answer("{\"wins_bedwars\":3}"), "notch", 0L));
        assertTrue(noShop.getMessage().startsWith("Notch has no Bed Wars quick buy"), noShop.getMessage());
        assertThrows(IllegalArgumentException.class, () -> HypixelProfile.fromApi(
                "{\"success\":true,\"player\":{\"displayname\":\"Notch\"}}", "notch", 0L), "no stats at all");
    }

    @Test
    void reportsAnswersThatAreNotAPlayer() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> HypixelProfile.fromApi("{\"success\":false,\"cause\":\"Invalid API key\"}", "notch", 0L));
        assertEquals("Hypixel refused the request: Invalid API key", refused.getMessage());
        assertThrows(IllegalStateException.class, () -> HypixelProfile.fromApi("<html>", "notch", 0L));
        assertThrows(IllegalStateException.class, () -> HypixelProfile.fromApi("{\"success\":", "notch", 0L));
        assertThrows(IllegalStateException.class, () -> HypixelProfile.fromApi("", "notch", 0L));
    }

    @Test
    void splitsTheLists() {
        assertEquals(Arrays.asList("wool", null, null, "tnt", null), HypixelProfile.split("wool, null,,tnt,NULL"));
    }

    @Test
    void keepsTheSettingsInAFile() throws Exception {
        HypixelProfile profile = new HypixelProfile("Notch", Arrays.asList("wool", null, "tnt"),
                Arrays.asList("Melee", null, "Blocks"), 7L);
        HypixelProfile read = HypixelProfile.fromYaml(profile.toYaml());
        assertEquals("Notch", read.name());
        assertEquals(profile.quickBuy(), read.quickBuy());
        assertEquals(profile.hotbar(), read.hotbar());
        assertEquals(7L, read.importedAt());
        assertNull(HypixelProfile.fromYaml(new HypixelProfile("Notch", Arrays.asList("wool"), null, 0L).toYaml()).hotbar());
    }

    @Test
    void readsMojangIds() {
        UUID notch = HypixelProfiles.undashed("069a79f444e94726a5befca90e38aaf5");
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), notch);
        assertEquals(4, notch.version(), "a Mojang account: what an online-mode server gives its players");
        assertEquals(notch, HypixelProfiles.undashed("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertThrows(IllegalStateException.class, () -> HypixelProfiles.undashed("notch"));
    }
}
