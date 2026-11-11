package gg.rago.lobby;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The ranked season (config.yml, saison:), as on the Ranked Bedwars servers: its number, its name
 * and the day it ends. A new number starts every ranked ELO again from 0; the best ELO each player
 * reached in the season that ended stays in their profile, and its division gives Rago Points
 * (recompenses), at their next arrival. Immutable.
 */
final class Season {

    /** The first season with the divisions: the ELO of an older one (from 1000) gives nothing. */
    static final int FIRST_WITH_DIVISIONS = 2;
    static final Season DEFAULT = new Season(FIRST_WITH_DIVISIONS, null, null, defaultRewards());
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final int number;
    private final String name;
    private final LocalDate end;
    /** Rago Points by the division reached, the lowest first. */
    private final Map<String, Integer> rewards;

    Season(int number, String name, LocalDate end, Map<String, Integer> rewards) {
        this.number = number;
        this.name = name == null || name.trim().isEmpty() ? "Season " + number : name.trim();
        this.end = end;
        this.rewards = Collections.unmodifiableMap(new LinkedHashMap<>(rewards));
    }

    private static Map<String, Integer> defaultRewards() {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        rewards.put("Coal", 0);
        rewards.put("Iron", 250);
        rewards.put("Gold", 500);
        rewards.put("Diamond", 1000);
        rewards.put("Emerald", 1500);
        rewards.put("Sapphire", 2000);
        rewards.put("Ruby", 3000);
        rewards.put("Crystal", 4000);
        rewards.put("Opal", 5000);
        rewards.put("Amethyst", 7500);
        rewards.put("Obsidian", 10000);
        return rewards;
    }

    int number() {
        return number;
    }

    /** "Season 2", or the name of config.yml. */
    String name() {
        return name;
    }

    /** The last day of the season, or null when none is set. */
    LocalDate end() {
        return end;
    }

    /** The Rago Points of a season ended in this division (0 for a division without reward). */
    int reward(String division) {
        Integer points = rewards.get(division);
        return points == null ? 0 : points;
    }

    /** The rewards, by division, the lowest first. */
    Map<String, Integer> rewards() {
        return rewards;
    }

    /**
     * "ends on 31/01/2027 (81 days left)", "ends today", "ended on 31/01/2027: the next one comes
     * soon", or "" without an end.
     */
    String endText(long now) {
        if (end == null) {
            return "";
        }
        LocalDate today = Instant.ofEpochMilli(now).atZone(StatsPeriod.ZONE).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, end);
        if (days < 0) {
            return "ended on " + end.format(SHOWN) + ": the next one comes soon";
        }
        if (days == 0) {
            return "ends today";
        }
        return "ends on " + end.format(SHOWN) + " (" + days + (days == 1 ? " day" : " days") + " left)";
    }

    /** The section of config.yml (null: the default season). Throws IllegalArgumentException naming the wrong setting. */
    static Season read(ConfigurationSection section, EloLadder ladder) {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        Map<String, Integer> defaults = defaultRewards();
        for (EloLadder.Division division : ladder.divisions()) {
            Integer points = defaults.get(division.name());
            rewards.put(division.name(), points == null ? 0 : points);
        }
        if (section == null) {
            return new Season(DEFAULT.number, null, null, rewards);
        }
        int number = section.getInt("numero", FIRST_WITH_DIVISIONS);
        if (number < FIRST_WITH_DIVISIONS || number > 1000) {
            throw new IllegalArgumentException("saison.numero: from " + FIRST_WITH_DIVISIONS + " to 1000");
        }
        String name = section.getString("nom", null);
        if (name != null && name.length() > 32) {
            throw new IllegalArgumentException("saison.nom: 32 characters at most");
        }
        LocalDate end = null;
        String endText = section.getString("fin", null);
        if (endText != null && !endText.trim().isEmpty()) {
            try {
                end = LocalDate.parse(endText.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("saison.fin: a day as 2027-01-31, not \"" + endText + "\"");
            }
        }
        ConfigurationSection given = section.getConfigurationSection("recompenses");
        if (given != null) {
            for (String key : given.getKeys(false)) {
                if (!rewards.containsKey(key)) {
                    throw new IllegalArgumentException("saison.recompenses." + key + ": not a division (" + String.join(", ",
                            rewards.keySet()) + ")");
                }
                int points = given.getInt(key, 0);
                if (points < 0 || points > ServerSettings.MAX_POINTS) {
                    throw new IllegalArgumentException("saison.recompenses." + key + ": from 0 to " + ServerSettings.MAX_POINTS);
                }
                rewards.put(key, points);
            }
        }
        return new Season(number, name == null ? null : LobbySettings.colour(name), end, rewards);
    }
}
