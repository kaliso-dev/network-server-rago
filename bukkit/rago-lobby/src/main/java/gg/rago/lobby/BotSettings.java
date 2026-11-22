package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * RagoBot as config.yml sets it (ragobot:): a player of the server, invited into a party with
 * /p invite RagoBot, who plays the party's games with it, as a real player, at one fixed skill.
 * Never ranked, never a random queue. Without ragobot:, the values of the bundled config.
 * Immutable.
 */
final class BotSettings {

    /** The name it plays under, the same for every RagoBot online (one per party). */
    static final String NAME = "RagoBot";
    /**
     * The modes of the bundled config: those the owner asked for first (22/11), then Boxing and
     * Lobby Fight 1v1, step 1 of the owner's order of 22/11 (17h54).
     */
    static final List<String> DEFAULT_MODES = Collections.unmodifiableList(
            Arrays.asList("top-bridge-1v1", "top-bridge-2v2", "sumo", "boxing", "lobby-fight-1v1"));
    static final int MAX_BOTS = 50;

    /** False: /p invite RagoBot finds nobody. */
    final boolean enabled;
    /** The account whose skin it wears (qRago). */
    final String skin;
    /** RagoBots online at once at most, one per party. */
    final int max;
    /** The ids of the modes it plays, in the order of the config. */
    final List<String> modes;
    /** The names of these modes, for the messages ("Top Bridge 1v1, Top Bridge 2v2 and Sumo 1v1"). */
    final String modeNames;
    final BotBrain.Skill skill;

    private BotSettings(boolean enabled, String skin, int max, List<String> modes, String modeNames, BotBrain.Skill skill) {
        this.enabled = enabled;
        this.skin = skin;
        this.max = max;
        this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
        this.modeNames = modeNames;
        this.skill = skill;
    }

    /** True if it plays this mode. */
    boolean plays(String modeId) {
        return modes.contains(modeId);
    }

    /**
     * ragobot: of config.yml; byId the modes of modes:. Its modes must exist, be played by two
     * teams, and not be ranked: RagoBot never moves an ELO.
     */
    static BotSettings read(ConfigurationSection section, Map<String, GameMode> byId) {
        boolean enabled = section == null || section.getBoolean("actif", true);
        String skin = section == null ? "qRago" : section.getString("skin", "qRago");
        if (!AccountSkins.validName(skin)) {
            throw new IllegalArgumentException("ragobot.skin: the name of a Minecraft account, as qRago");
        }
        int max = section == null ? 10 : section.getInt("max", 10);
        if (max < 1 || max > MAX_BOTS) {
            throw new IllegalArgumentException("ragobot.max: from 1 to " + MAX_BOTS + " RagoBots online at once");
        }
        List<String> modes = new ArrayList<>();
        List<String> listed = section == null || !section.isList("modes") ? DEFAULT_MODES : section.getStringList("modes");
        if (section != null && section.contains("modes") && !section.isList("modes")) {
            throw new IllegalArgumentException("ragobot.modes: a list of modes, as [top-bridge-1v1, sumo]");
        }
        List<String> names = new ArrayList<>();
        for (String id : listed) {
            GameMode mode = byId.get(id);
            if (mode == null) {
                if (section == null) {
                    // the bundled modes, in a config without them: RagoBot plays the others
                    continue;
                }
                throw new IllegalArgumentException("ragobot.modes: no mode " + id + " in modes");
            }
            if (mode.ranked()) {
                throw new IllegalArgumentException("ragobot.modes: " + id + " is ranked, RagoBot never plays ranked");
            }
            if (mode.teams() < 2) {
                throw new IllegalArgumentException("ragobot.modes: " + id + " has one team, RagoBot plays against the others");
            }
            if (!modes.contains(id)) {
                modes.add(id);
                names.add(org.bukkit.ChatColor.stripColor(LobbySettings.colour(mode.name())));
            }
        }
        BotBrain.Skill skill = readSkill(section == null ? null : section.getConfigurationSection("skill"));
        return new BotSettings(enabled, skin, max, modes, join(names), skill);
    }

    private static BotBrain.Skill readSkill(ConfigurationSection section) {
        BotBrain.Skill bundled = BotBrain.Skill.BUNDLED;
        if (section == null) {
            return bundled;
        }
        double cps = number(section, "cps", bundled.cps, 1, 20);
        double accuracy = number(section, "precision", bundled.accuracy, 0, 1);
        double reach = number(section, "portee", bundled.reach, 1, 3);
        double strafe = number(section, "strafe", bundled.strafe, 0, 1);
        double wTap = number(section, "w-tap", bundled.wTap, 0, 1);
        double jumpReset = number(section, "jump-reset", bundled.jumpReset, 0, 1);
        int reaction = (int) number(section, "reaction", bundled.reaction, 0, BotBrain.MEMORY - 1);
        double turn = number(section, "rotation", bundled.turn, 5, 180);
        double healAt = number(section, "soin-a", bundled.healAt, 0, 20);
        return new BotBrain.Skill(cps, accuracy, reach, strafe, wTap, jumpReset, reaction, turn, healAt);
    }

    private static double number(ConfigurationSection section, String key, double bundled, double low, double high) {
        if (section.contains(key) && !section.isDouble(key) && !section.isInt(key)) {
            throw new IllegalArgumentException("ragobot.skill." + key + ": a number from " + plain(low) + " to " + plain(high));
        }
        double value = section.getDouble(key, bundled);
        if (value < low || value > high) {
            throw new IllegalArgumentException("ragobot.skill." + key + ": from " + plain(low) + " to " + plain(high));
        }
        return value;
    }

    private static String plain(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** "a, b and c". */
    static String join(List<String> names) {
        if (names.isEmpty()) {
            return "no mode";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
