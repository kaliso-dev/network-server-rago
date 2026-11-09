package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One player's stats as a YAML file (plugins/RagoLobby/stats/&lt;uuid&gt;.yml), and the team win
 * streaks of each mode (plugins/RagoLobby/stats/equipes/&lt;mode&gt;.yml), until the network's
 * database exists. Written on the main thread, read anywhere.
 */
final class StatsFiles {

    static final String FOLDER = "stats";
    /** The team streaks, in the stats folder: one file per mode. */
    static final String TEAMS_FOLDER = "equipes";
    /**
     * The ELO of a file of another season is not read: the season of the divisions (from 0, as on
     * the Ranked Bedwars servers) started everyone again from 0.
     */
    static final int ELO_SEASON = 2;

    private StatsFiles() {
    }

    static String fileName(UUID player) {
        return player + ".yml";
    }

    static String toYaml(PlayerStats stats) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Stats of " + stats.name() + ", written by RagoLobby.");
        yaml.set("nom", stats.name());
        yaml.set("xp", stats.xp());
        yaml.set("elo-saison", ELO_SEASON);
        yaml.set("profil.points", stats.points());
        if (stats.rank() != null) {
            yaml.set("profil.rang", stats.rank());
        }
        yaml.set("profil.rangs-achetes", new ArrayList<>(stats.boughtRanks()));
        yaml.set("profil.cosmetiques", new ArrayList<>(stats.cosmetics()));
        for (Cosmetic.Type type : Cosmetic.Type.values()) {
            if (stats.equipped(type) != null) {
                yaml.set("profil.equipes." + type.id(), stats.equipped(type));
            }
        }
        List<String> friends = new ArrayList<>();
        for (UUID friend : stats.friends()) {
            friends.add(friend.toString());
        }
        yaml.set("profil.amis", friends);
        yaml.set("profil.reglages.messages", stats.messages().id());
        yaml.set("profil.reglages.parties", stats.partyInvites());
        yaml.set("profil.reglages.amis", stats.friendRequests());
        yaml.set("profil.reglages.duels", stats.duelRequests());
        yaml.set("profil.quotidien.date", stats.dailyDay());
        yaml.set("profil.quotidien.serie", stats.dailyStreak());
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

    /** The win streaks of a mode's teams. */
    static String teamsToYaml(String modeId, Collection<TeamStreak> teams) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Win streaks of the teams of the mode " + modeId + ", written by RagoLobby.");
        List<Map<String, Object>> list = new ArrayList<>();
        for (TeamStreak team : teams) {
            Map<String, Object> entry = new LinkedHashMap<>();
            List<String> ids = new ArrayList<>();
            for (UUID id : team.players()) {
                ids.add(id.toString());
            }
            entry.put("joueurs", ids);
            entry.put("serie", team.streak);
            entry.put("meilleure-serie", team.best);
            entry.put("jour", team.day);
            entry.put("jour-meilleure-serie", team.dayBest);
            entry.put("semaine", team.week);
            entry.put("semaine-meilleure-serie", team.weekBest);
            list.add(entry);
        }
        yaml.set("equipes", list);
        return yaml.saveToString();
    }

    /** The teams of this file's text; a team with an unreadable player is left out. */
    static List<TeamStreak> teamsFromYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        List<TeamStreak> teams = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("equipes")) {
            Object ids = entry.get("joueurs");
            if (!(ids instanceof List) || ((List<?>) ids).isEmpty()) {
                continue;
            }
            List<UUID> players = new ArrayList<>();
            try {
                for (Object id : (List<?>) ids) {
                    players.add(UUID.fromString(String.valueOf(id)));
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
            TeamStreak team = new TeamStreak(players);
            team.streak = count(entry.get("serie"));
            team.best = Math.max(team.streak, count(entry.get("meilleure-serie")));
            team.day = entry.get("jour") == null ? "" : String.valueOf(entry.get("jour"));
            team.dayBest = count(entry.get("jour-meilleure-serie"));
            team.week = entry.get("semaine") == null ? "" : String.valueOf(entry.get("semaine"));
            team.weekBest = count(entry.get("semaine-meilleure-serie"));
            teams.add(team);
        }
        return teams;
    }

    private static int count(Object value) {
        return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
    }

    /** The stats of this file's text. */
    static PlayerStats fromYaml(UUID player, String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        PlayerStats stats = new PlayerStats(player, yaml.getString("nom", "?"));
        stats.addXp(Math.max(0, yaml.getInt("xp")));
        boolean thisSeason = yaml.getInt("elo-saison", 1) == ELO_SEASON;
        stats.addPoints(Math.max(0, yaml.getInt("profil.points")));
        String rank = yaml.getString("profil.rang", null);
        stats.setRank(rank == null || rank.trim().isEmpty() ? null : rank.trim());
        stats.boughtRanks().addAll(yaml.getStringList("profil.rangs-achetes"));
        stats.cosmetics().addAll(yaml.getStringList("profil.cosmetiques"));
        for (Cosmetic.Type type : Cosmetic.Type.values()) {
            String equipped = yaml.getString("profil.equipes." + type.id(), null);
            if (equipped != null && stats.cosmetics().contains(equipped)) {
                stats.equip(type, equipped);
            }
        }
        for (String friend : yaml.getStringList("profil.amis")) {
            try {
                stats.friends().add(UUID.fromString(friend));
            } catch (IllegalArgumentException e) {
                // an unreadable friend is left out
            }
        }
        stats.setMessages(PlayerStats.MessageSetting.of(yaml.getString("profil.reglages.messages", "all")));
        stats.setPartyInvites(yaml.getBoolean("profil.reglages.parties", true));
        stats.setFriendRequests(yaml.getBoolean("profil.reglages.amis", true));
        stats.setDuelRequests(yaml.getBoolean("profil.reglages.duels", true));
        stats.setDaily(yaml.getString("profil.quotidien.date", ""), yaml.getInt("profil.quotidien.serie"));
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
                m.elo = thisSeason ? Math.max(0, section.getInt("elo")) : 0;
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
