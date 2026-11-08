package gg.rago.lobby;

import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One player's stats as a YAML file (plugins/RagoLobby/stats/&lt;uuid&gt;.yml) until the network's
 * database exists. Written from {@link #toYaml} on the main thread, read by {@link #fromYaml}
 * anywhere.
 */
final class StatsFiles {

    static final String FOLDER = "stats";

    private StatsFiles() {
    }

    static String fileName(UUID player) {
        return player + ".yml";
    }

    static String toYaml(PlayerStats stats) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Stats de " + stats.name() + ", écrites par RagoLobby.");
        yaml.set("nom", stats.name());
        yaml.set("xp", stats.xp());
        for (Map.Entry<String, ModeStats> entry : stats.modes().entrySet()) {
            ModeStats m = entry.getValue();
            ConfigurationSection section = yaml.createSection("modes." + entry.getKey());
            section.set("victoires", m.wins);
            section.set("defaites", m.losses);
            section.set("serie", m.streak);
            section.set("meilleure-serie", m.bestStreak);
            if (m.rated()) {
                section.set("elo", m.elo);
            }
            section.set("kills", m.kills);
            section.set("morts", m.deaths);
            section.set("jour.date", m.day);
            section.set("jour.victoires", m.dayWins);
            section.set("jour.parties", m.dayGames);
            section.set("jour.meilleure-serie", m.dayBest);
            section.set("jour.kills", m.dayKills);
            section.set("semaine.date", m.week);
            section.set("semaine.victoires", m.weekWins);
            section.set("semaine.parties", m.weekGames);
            section.set("semaine.meilleure-serie", m.weekBest);
            section.set("semaine.kills", m.weekKills);
        }
        return yaml.saveToString();
    }

    /** The stats of this file's text. */
    static PlayerStats fromYaml(UUID player, String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        PlayerStats stats = new PlayerStats(player, yaml.getString("nom", "?"));
        stats.addXp(Math.max(0, yaml.getInt("xp")));
        ConfigurationSection modes = yaml.getConfigurationSection("modes");
        if (modes != null) {
            for (String modeId : modes.getKeys(false)) {
                ConfigurationSection section = modes.getConfigurationSection(modeId);
                if (section == null) {
                    continue;
                }
                ModeStats m = stats.modeOrNew(modeId);
                m.wins = Math.max(0, section.getInt("victoires"));
                m.losses = Math.max(0, section.getInt("defaites"));
                m.streak = Math.max(0, section.getInt("serie"));
                m.bestStreak = Math.max(m.streak, section.getInt("meilleure-serie"));
                m.elo = Math.max(0, section.getInt("elo"));
                m.kills = Math.max(0, section.getInt("kills"));
                m.deaths = Math.max(0, section.getInt("morts"));
                m.day = section.getString("jour.date", "");
                m.dayWins = Math.max(0, section.getInt("jour.victoires"));
                m.dayGames = Math.max(0, section.getInt("jour.parties"));
                m.dayBest = Math.max(0, section.getInt("jour.meilleure-serie"));
                m.dayKills = Math.max(0, section.getInt("jour.kills"));
                m.week = section.getString("semaine.date", "");
                m.weekWins = Math.max(0, section.getInt("semaine.victoires"));
                m.weekGames = Math.max(0, section.getInt("semaine.parties"));
                m.weekBest = Math.max(0, section.getInt("semaine.meilleure-serie"));
                m.weekKills = Math.max(0, section.getInt("semaine.kills"));
            }
        }
        return stats;
    }
}
