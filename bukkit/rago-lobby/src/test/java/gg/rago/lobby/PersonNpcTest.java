package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The people of the thank-you room (pnj: ... type: personne, plan 5.6) and the skins of their accounts. */
class PersonNpcTest {

    @Test
    void qRagoAndKalisoDevOfTheBundledConfig() throws Exception {
        YamlConfiguration bundled = LobbySettingsTest.bundledConfig();
        LobbySettings settings = LobbySettings.load(bundled);
        Ranks ranks = ServerSettings.load(bundled).ranks();
        LobbySettings.checkRanks(settings.npcs(), ranks);

        LobbyNpc owner = settings.npc("qrago");
        assertEquals(LobbyNpc.Kind.PERSON, owner.kind());
        assertTrue(owner.hasBody());
        assertEquals("qRago", owner.account(), "the skin of the account qRago");
        assertNull(owner.skin(), "no PNG of the jar");
        assertTrue(owner.modeIds().isEmpty());
        assertEquals("§4[OWNER] §4qRago", LobbyNpcs.personName(owner.name(), ranks.get(owner.rank())),
                "the name after the prefix of the Owner rank, as above a player's head");
        assertEquals(Material.OBSIDIAN, settings.pedestal("qrago").material(), "on obsidian, one block higher");

        LobbyNpc dev = settings.npc("kalisodev");
        assertEquals(LobbyNpc.Kind.PERSON, dev.kind());
        assertEquals("kaliso_dev", dev.account());
        assertEquals("§b[DEV] §bkaliso_dev", LobbyNpcs.personName(dev.name(), ranks.get(dev.rank())));
        assertEquals(Material.OBSIDIAN, settings.pedestal("kalisodev").material());
    }

    @Test
    void aPersonWithoutARankShowsTheirNameAlone() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("pnj.qrago.rang", null);
        LobbyNpc owner = LobbySettings.load(yaml).npc("qrago");
        assertNull(owner.rank());
        assertEquals("qRago", LobbyNpcs.personName(owner.name(), null));
    }

    @Test
    void wrongPeopleAreRefused() throws Exception {
        YamlConfiguration unknownRank = LobbySettingsTest.bundledConfig();
        unknownRank.set("pnj.qrago.rang", "king");
        LobbySettings settings = LobbySettings.load(unknownRank);
        IllegalArgumentException rank = assertThrows(IllegalArgumentException.class,
                () -> LobbySettings.checkRanks(settings.npcs(), ServerSettings.load(unknownRank).ranks()));
        assertTrue(rank.getMessage().startsWith("pnj.qrago.rang"), rank.getMessage());

        for (String name : new String[] {"q Rago", "qR", "a_name_of_17_char", "qRago!"}) {
            YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
            yaml.set("pnj.qrago.skin-joueur", name);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml), name);
            assertTrue(e.getMessage().startsWith("pnj.qrago.skin-joueur"), e.getMessage());
        }

        YamlConfiguration both = LobbySettingsTest.bundledConfig();
        both.set("pnj.qrago.skin", "stats");
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(both));
        assertTrue(twice.getMessage().contains("skin or skin-joueur"), twice.getMessage());

        YamlConfiguration modes = LobbySettingsTest.bundledConfig();
        modes.set("pnj.qrago.modes", Collections.singletonList("sumo"));
        assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(modes), "a person opens no modes");
    }

    @Test
    void anyNpcCanWearTheSkinOfAnAccount() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("pnj.shop.skin", null);
        yaml.set("pnj.shop.skin-joueur", "Notch");
        LobbyNpc shop = LobbySettings.load(yaml).npc("shop");
        assertEquals(LobbyNpc.Kind.SHOP, shop.kind());
        assertEquals("Notch", shop.account());
        assertNull(LobbySettings.load(LobbySettingsTest.bundledConfig()).npc("shop").account(), "skin: alone, as before");
    }

    @Test
    void theSkinsOfTheAccountsAreKeptByName() throws Exception {
        assertTrue(AccountSkins.validName("qRago") && AccountSkins.validName("kaliso_dev") && AccountSkins.validName("abc"));
        assertFalse(AccountSkins.validName("ab") || AccountSkins.validName("seventeen_letters") || AccountSkins.validName("a-b")
                || AccountSkins.validName("") || AccountSkins.validName(null));

        Map<String, Skin> skins = new TreeMap<>();
        skins.put("qrago", new Skin("dGV4dHVyZXM=", "c2lnbmVk"));
        skins.put("kaliso_dev", new Skin("b3RoZXI=", "c2lnbmVkMg=="));
        assertEquals(skins, AccountSkins.read(AccountSkins.yaml(skins)), "written, then read back the same");

        Map<String, Skin> read = AccountSkins.read("qRago:\n  value: v\n  signature: s\nnot a name:\n  value: v\n  signature: s\n"
                + "unsigned:\n  value: v\n");
        assertEquals(Collections.singleton("qrago"), read.keySet(), "by name in lowercase; neither a bad name nor a skin unsigned");
        assertEquals(new Skin("v", "s"), read.get("qrago"));
    }
}
