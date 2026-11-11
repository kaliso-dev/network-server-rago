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
     * The season of the ELO when none is set (saison: in config.yml): the season of the divisions
     * (from 0, as on the Ranked Bedwars servers) started everyone again from 0. The ELO of a file of
     * another season is not read: a new season starts everyone again from 0.
     */
    static final int ELO_SEASON = Season.FIRST_WITH_DIVISIONS;

    private StatsFiles() {
    }

    static String fileName(UUID player) {
        return player + ".yml";
    }

    static String toYaml(PlayerStats stats) {
        return toYaml(stats, ELO_SEASON);
    }

    /** The file of the player's stats, their ELO of this season. */
    static String toYaml(PlayerStats stats, int season) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Stats of " + stats.name() + ", written by RagoLobby.");
        yaml.set("nom", stats.name());
        yaml.set("xp", stats.xp());
        yaml.set("elo-saison", season);
        for (Map.Entry<Integer, PlayerStats.SeasonResult> ended : stats.seasons().entrySet()) {
            String path = "profil.saisons." + ended.getKey();
            yaml.set(path + ".elo", ended.getValue().elo);
            yaml.set(path + ".division", ended.getValue().division);
            yaml.set(path + ".recompense", ended.getValue().rewarded);
        }
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
        yaml.set("profil.reglages.vitesse", stats.lobbySpeed());
        yaml.set("profil.reglages.rejouer", stats.autoRequeue());
        yaml.set("profil.reglages.heure", stats.timeOfDay().id());
        yaml.set("profil.gadget", stats.gadget());
        yaml.set("profil.quotidien.date", stats.dailyDay());
        yaml.set("profil.quotidien.serie", stats.dailyStreak());
        if (stats.colourChoice() != null) {
            yaml.set("profil.style.couleur", stats.colourChoice());
        }
        if (stats.badgeChoice() != null) {
            yaml.set("profil.style.badge", stats.badgeChoice());
        }
        if (stats.plusChoice() != null) {
            yaml.set("profil.style.plus", stats.plusChoice());
        }
        if (stats.boosters() > 0 || stats.dailyBoosters() > 0 || stats.boosterLeftMs() > 0 || stats.boosterDay() != null) {
            yaml.set("profil.boosters.nombre", stats.boosters());
            yaml.set("profil.boosters.gratuits", stats.dailyBoosters());
            yaml.set("profil.boosters.reste-ms", stats.boosterLeftMs());
            yaml.set("profil.boosters.multiplicateur", stats.boosterRate());
            yaml.set("profil.boosters.jour-gratuit", stats.boosterDay());
        }
        if (stats.coinBoosters() > 0 || stats.coinBoosterLeftMs() > 0) {
            yaml.set("profil.boosters-pieces.nombre", stats.coinBoosters());
            yaml.set("profil.boosters-pieces.reste-ms", stats.coinBoosterLeftMs());
        }
        if (stats.headLine() != null) {
            yaml.set("profil.tete.affichage", stats.headLine());
        }
        if (stats.headMessage() != null) {
            yaml.set("profil.tete.message", stats.headMessage());
        }
        if (stats.skin() != null) {
            yaml.set("profil.skin.valeur", stats.skin().value());
            yaml.set("profil.skin.signature", stats.skin().signature());
        }
        if (stats.nick() != null) {
            yaml.set("profil.nick", stats.nick());
        }
        yaml.set("profil.recompenses-niveau", stats.rewardsLevel());
        yaml.set("profil.quetes.jour", stats.questDay());
        yaml.set("profil.quetes.semaine", stats.questWeek());
        for (Map.Entry<String, Integer> quest : stats.quests().entrySet()) {
            // "d:gagner" is "d-gagner" in the file: a ":" would read as a key and a value
            yaml.set("profil.quetes.progres." + quest.getKey().replace(':', '-'), quest.getValue());
        }
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
            if (m.peakElo > m.elo) {
                section.set("elo-max", m.peakElo);
            }
            section.set("kills", m.kills);
            section.set("morts", m.deaths);
            if (m.finalKills > 0) {
                section.set("final-kills", m.finalKills);
            }
            if (m.finalDeaths > 0) {
                section.set("morts-finales", m.finalDeaths);
            }
            if (m.beds > 0) {
                section.set("lits", m.beds);
            }
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
        return fromYaml(player, text, ELO_SEASON, EloLadder.DEFAULT);
    }

    /**
     * The stats of this file's text, in this season: the ELO of a file of another season is not
     * read, and when that season ended (an older one, with divisions) the best ELO the player
     * reached in it becomes their result of that season, its reward still to give.
     */
    static PlayerStats fromYaml(UUID player, String text, int season, EloLadder ladder) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        PlayerStats stats = new PlayerStats(player, yaml.getString("nom", "?"));
        stats.addXp(Math.max(0, yaml.getInt("xp")));
        int fileSeason = yaml.getInt("elo-saison", 1);
        boolean thisSeason = fileSeason == season;
        ConfigurationSection ended = yaml.getConfigurationSection("profil.saisons");
        if (ended != null) {
            for (String key : ended.getKeys(false)) {
                try {
                    int number = Integer.parseInt(key);
                    String division = ended.getString(key + ".division", "");
                    stats.seasons().put(number, new PlayerStats.SeasonResult(Math.max(0, ended.getInt(key + ".elo")),
                            division.isEmpty() ? ladder.division(Math.max(0, ended.getInt(key + ".elo"))).name() : division,
                            ended.getBoolean(key + ".recompense", true)));
                } catch (NumberFormatException e) {
                    // not a season: left out
                }
            }
        }
        int seasonBest = 0;
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
        stats.setLobbySpeed(yaml.getInt("profil.reglages.vitesse", 0));
        stats.setAutoRequeue(yaml.getBoolean("profil.reglages.rejouer", false));
        stats.setTimeOfDay(PlayerStats.TimeOfDay.of(yaml.getString("profil.reglages.heure", "jour")));
        stats.setGadget(yaml.getString("profil.gadget", null));
        stats.setDaily(yaml.getString("profil.quotidien.date", ""), yaml.getInt("profil.quotidien.serie"));
        stats.choose(NameStyle.Kind.COLOUR, yaml.getString("profil.style.couleur", null));
        stats.choose(NameStyle.Kind.BADGE, yaml.getString("profil.style.badge", null));
        stats.choose(NameStyle.Kind.PLUS, yaml.getString("profil.style.plus", null));
        stats.setBoosters(yaml.getInt("profil.boosters.nombre", 0));
        stats.setDailyBoosters(yaml.getInt("profil.boosters.gratuits", 0));
        stats.setBoosterLeftMs(yaml.getLong("profil.boosters.reste-ms", 0L));
        stats.setBoosterRate(yaml.getDouble("profil.boosters.multiplicateur", 0));
        stats.setCoinBoosters(yaml.getInt("profil.boosters-pieces.nombre", 0));
        stats.setCoinBoosterLeftMs(yaml.getLong("profil.boosters-pieces.reste-ms", 0L));
        stats.setBoosterDay(yaml.getString("profil.boosters.jour-gratuit", null));
        stats.setHeadLine(yaml.getString("profil.tete.affichage", null));
        stats.setHeadMessage(yaml.getString("profil.tete.message", null));
        stats.setNick(yaml.getString("profil.nick", null));
        stats.setSkin(Skin.of(yaml.getString("profil.skin.valeur", null), yaml.getString("profil.skin.signature", null)));
        stats.setRewardsLevel(yaml.getInt("profil.recompenses-niveau"));
        stats.setQuestPeriod(yaml.getString("profil.quetes.jour", ""), yaml.getString("profil.quetes.semaine", ""));
        ConfigurationSection quests = yaml.getConfigurationSection("profil.quetes.progres");
        if (quests != null) {
            for (String key : quests.getKeys(false)) {
                if (key.matches("[dw]-.+")) {
                    stats.setQuestProgress(key.charAt(0) + ":" + key.substring(2), Math.max(0, quests.getInt(key)));
                }
            }
        }
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
                int elo = Math.max(0, section.getInt("elo"));
                int peak = Math.max(elo, section.getInt("elo-max"));
                seasonBest = Math.max(seasonBest, peak);
                m.elo = thisSeason ? elo : 0;
                m.peakElo = thisSeason ? peak : 0;
                m.kills = Math.max(0, section.getInt("kills"));
                m.deaths = Math.max(0, section.getInt("morts"));
                m.finalKills = Math.max(0, section.getInt("final-kills"));
                m.finalDeaths = Math.max(0, section.getInt("morts-finales"));
                m.beds = Math.max(0, section.getInt("lits"));
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
        if (!thisSeason && fileSeason >= Season.FIRST_WITH_DIVISIONS && fileSeason < season && seasonBest > 0
                && !stats.seasons().containsKey(fileSeason)) {
            stats.seasons().put(fileSeason, new PlayerStats.SeasonResult(seasonBest, ladder.division(seasonBest).name(), false));
        }
        return stats;
    }
}
