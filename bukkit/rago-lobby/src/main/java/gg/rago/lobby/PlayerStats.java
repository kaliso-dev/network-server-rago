package gg.rago.lobby;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's stats, mode by mode, and their profile: Rago Points, ranks, cosmetics, friends,
 * settings and daily reward. Main thread only.
 */
final class PlayerStats {

    private final UUID id;
    private String name;
    private final Map<String, ModeStats> modes = new LinkedHashMap<>();
    /** Network XP, every mode together (see {@link NetworkLevel}). */
    private int xp;
    /** Rago Points, the money of the shop. */
    private int points;
    /** The rank the staff gave (/rank set), or null. */
    private String rank;
    private final Set<String> boughtRanks = new LinkedHashSet<>();
    private final Set<String> cosmetics = new LinkedHashSet<>();
    /** The cosmetic equipped of each type, by the id of the type. */
    private final Map<String, String> equipped = new HashMap<>();
    private final Set<UUID> friends = new LinkedHashSet<>();
    private MessageSetting messages = MessageSetting.ALL;
    private boolean partyInvites = true;
    private boolean friendRequests = true;
    private boolean duelRequests = true;
    /** The Speed of the lobby (PREMIUM): 0 for none, else the level of the effect, 1 to 3. */
    private int lobbySpeed;
    /** Back in the queue of the same mode after each game, without a click (/settings). */
    private boolean autoRequeue;
    /** The time of day the player sees, in the lobby and in games (/settings). */
    private TimeOfDay timeOfDay = TimeOfDay.DAY;
    /** The gadget of the hotbar (the name of its HotbarItem), null for the teleport stick. */
    private String gadget;
    /** The day the daily reward was last claimed ("" never), and the days in a row. */
    private String dailyDay = "";
    private int dailyStreak;
    /** The colour of name and the badge chosen (/style): null for the rank's, "" for none. */
    private String colourChoice;
    private String badgeChoice;
    private String plusChoice;
    /**
     * The XP boosters waiting (bought, x3), the free ones of PREMIUM waiting (x2), the time left of
     * the one running and what it multiplies by, the day of the last free one; the coin boosters
     * waiting and the time left of the one running.
     */
    private int boosters;
    private int dailyBoosters;
    private long boosterLeftMs;
    private double boosterRate;
    private String boosterDay;
    private int coinBoosters;
    private long coinBoosterLeftMs;
    /** What shows above the name (an id of {@link HeadLines.Shown}, null for the ELO), and the custom message. */
    private String headLine;
    private String headMessage;
    /** The name the player plays under (/nick), or null. */
    private String nick;
    /** Their skin at their last join, for the podiums, or null. */
    private Skin skin;
    /** The highest Network level whose rewards the player got. */
    private int rewardsLevel;
    /** The day and the week of the quests' progress, and the progress of each quest by its key. */
    private String questDay = "";
    private String questWeek = "";
    private final Map<String, Integer> quests = new LinkedHashMap<>();
    /** The ranked seasons that ended, by number: the best ELO reached, its division, rewarded or not. */
    private final Map<Integer, SeasonResult> seasons = new java.util.TreeMap<>();

    /** A ranked season that ended: the best ELO of the player in any ranked mode, and its division. */
    static final class SeasonResult {
        final int elo;
        final String division;
        /** True once its Rago Points were given (at the next arrival). */
        boolean rewarded;

        SeasonResult(int elo, String division, boolean rewarded) {
            this.elo = elo;
            this.division = division;
            this.rewarded = rewarded;
        }
    }

    /** Who may send this player private messages. */
    /** The time of day a player sees: the server's noon, a sunset or the night (their own sky only). */
    enum TimeOfDay {
        DAY("jour", "Day", -1L),
        SUNSET("coucher", "Sunset", 12500L),
        NIGHT("nuit", "Night", 18000L);

        private final String id;
        private final String label;
        private final long ticks;

        TimeOfDay(String id, String label, long ticks) {
            this.id = id;
            this.label = label;
            this.ticks = ticks;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        /** The time of the player's sky in ticks, or -1 for the server's own. */
        long ticks() {
            return ticks;
        }

        TimeOfDay next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static TimeOfDay of(String id) {
            for (TimeOfDay time : values()) {
                if (time.id.equals(id)) {
                    return time;
                }
            }
            return DAY;
        }
    }

    enum MessageSetting {
        ALL("all", "Everyone"), FRIENDS("friends", "Friends only"), NONE("none", "Nobody");

        private final String id;
        private final String label;

        MessageSetting(String id, String label) {
            this.id = id;
            this.label = label;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        MessageSetting next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static MessageSetting of(String id) {
            for (MessageSetting setting : values()) {
                if (setting.id.equals(id)) {
                    return setting;
                }
            }
            return ALL;
        }
    }

    PlayerStats(UUID id, String name) {
        this.id = id;
        this.name = name == null ? "?" : name;
    }

    UUID id() {
        return id;
    }

    /** The last name seen. */
    String name() {
        return name;
    }

    /** True if the name changed. */
    boolean rename(String newName) {
        if (newName == null || newName.equals(name)) {
            return false;
        }
        name = newName;
        return true;
    }

    int xp() {
        return xp;
    }

    void addXp(int gained) {
        xp = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) xp + gained));
    }

    int points() {
        return points;
    }

    /** Adds (or with a negative amount, takes) Rago Points; never below 0. */
    void addPoints(int amount) {
        points = (int) Math.min(ServerSettings.MAX_POINTS, Math.max(0L, (long) points + amount));
    }

    String rank() {
        return rank;
    }

    void setRank(String rank) {
        this.rank = rank;
    }

    Set<String> boughtRanks() {
        return boughtRanks;
    }

    Set<String> cosmetics() {
        return cosmetics;
    }

    /** The id of the cosmetic of this type the player equipped, or null. */
    String equipped(Cosmetic.Type type) {
        return equipped.get(type.id());
    }

    /** Equips this cosmetic of the type, or takes it off with null. */
    void equip(Cosmetic.Type type, String cosmeticId) {
        if (cosmeticId == null) {
            equipped.remove(type.id());
        } else {
            equipped.put(type.id(), cosmeticId);
        }
    }

    Set<UUID> friends() {
        return friends;
    }

    MessageSetting messages() {
        return messages;
    }

    void setMessages(MessageSetting messages) {
        this.messages = messages;
    }

    boolean partyInvites() {
        return partyInvites;
    }

    void setPartyInvites(boolean partyInvites) {
        this.partyInvites = partyInvites;
    }

    boolean friendRequests() {
        return friendRequests;
    }

    void setFriendRequests(boolean friendRequests) {
        this.friendRequests = friendRequests;
    }

    boolean duelRequests() {
        return duelRequests;
    }

    void setDuelRequests(boolean duelRequests) {
        this.duelRequests = duelRequests;
    }

    int lobbySpeed() {
        return lobbySpeed;
    }

    boolean autoRequeue() {
        return autoRequeue;
    }

    void setAutoRequeue(boolean autoRequeue) {
        this.autoRequeue = autoRequeue;
    }

    TimeOfDay timeOfDay() {
        return timeOfDay;
    }

    void setTimeOfDay(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay == null ? TimeOfDay.DAY : timeOfDay;
    }

    String gadget() {
        return gadget;
    }

    void setGadget(String gadget) {
        this.gadget = gadget;
    }

    void setLobbySpeed(int lobbySpeed) {
        this.lobbySpeed = Math.max(0, Math.min(RagoLobby.MAX_LOBBY_SPEED, lobbySpeed));
    }

    String dailyDay() {
        return dailyDay;
    }

    int dailyStreak() {
        return dailyStreak;
    }

    void setDaily(String day, int streak) {
        this.dailyDay = day == null ? "" : day;
        this.dailyStreak = Math.max(0, streak);
    }

    /** The id of the colour of name chosen: null for the one of the rank, "" for the plain colour of the rank. */
    String colourChoice() {
        return colourChoice;
    }

    /** The id of the badge chosen: null for the one of the rank, "" for none. */
    String badgeChoice() {
        return badgeChoice;
    }

    int boosters() {
        return boosters;
    }

    void setBoosters(int boosters) {
        this.boosters = Math.max(0, boosters);
    }

    /** The free XP boosters of PREMIUM waiting (x2). */
    int dailyBoosters() {
        return dailyBoosters;
    }

    void setDailyBoosters(int dailyBoosters) {
        this.dailyBoosters = Math.max(0, dailyBoosters);
    }

    /** What the XP booster running multiplies the XP by (0 when unknown: an old one, x2). */
    double boosterRate() {
        return boosterRate;
    }

    void setBoosterRate(double boosterRate) {
        this.boosterRate = Math.max(0, boosterRate);
    }

    /** The coin boosters waiting. */
    int coinBoosters() {
        return coinBoosters;
    }

    void setCoinBoosters(int coinBoosters) {
        this.coinBoosters = Math.max(0, coinBoosters);
    }

    /** The play time left of the coin booster running, 0 without one. */
    long coinBoosterLeftMs() {
        return coinBoosterLeftMs;
    }

    void setCoinBoosterLeftMs(long coinBoosterLeftMs) {
        this.coinBoosterLeftMs = Math.max(0, coinBoosterLeftMs);
    }

    /** The play time left of the XP booster running, 0 without one. */
    long boosterLeftMs() {
        return boosterLeftMs;
    }

    void setBoosterLeftMs(long boosterLeftMs) {
        this.boosterLeftMs = Math.max(0, boosterLeftMs);
    }

    /** The day ("2026-11-10") of the last free booster of PREMIUM, or null. */
    String boosterDay() {
        return boosterDay;
    }

    void setBoosterDay(String boosterDay) {
        this.boosterDay = boosterDay;
    }

    /** The id of the colour of + chosen: null or "" for the white +. */
    String plusChoice() {
        return plusChoice;
    }

    /** The id chosen of this kind of style (see the getters). */
    String choice(NameStyle.Kind kind) {
        return kind == NameStyle.Kind.COLOUR ? colourChoice : kind == NameStyle.Kind.BADGE ? badgeChoice : plusChoice;
    }

    void choose(NameStyle.Kind kind, String id) {
        if (kind == NameStyle.Kind.COLOUR) {
            colourChoice = id;
        } else if (kind == NameStyle.Kind.BADGE) {
            badgeChoice = id;
        } else {
            plusChoice = id;
        }
    }

    String headLine() {
        return headLine;
    }

    void setHeadLine(String headLine) {
        this.headLine = headLine;
    }

    String headMessage() {
        return headMessage;
    }

    void setHeadMessage(String headMessage) {
        this.headMessage = headMessage == null || headMessage.isEmpty() ? null : headMessage;
    }

    /** The name the player plays under, or null. */
    String nick() {
        return nick;
    }

    void setNick(String nick) {
        this.nick = nick == null || nick.isEmpty() ? null : nick;
    }

    /** Their skin at their last join (or as Mojang gave it), or null. */
    Skin skin() {
        return skin;
    }

    void setSkin(Skin skin) {
        this.skin = skin;
    }

    int rewardsLevel() {
        return rewardsLevel;
    }

    void setRewardsLevel(int level) {
        rewardsLevel = Math.max(0, level);
    }

    String questDay() {
        return questDay;
    }

    String questWeek() {
        return questWeek;
    }

    /** A new day forgets the progress of the daily quests, a new week that of the weekly ones. */
    void newQuestPeriod(String day, String week) {
        if (!day.equals(questDay)) {
            quests.keySet().removeIf(key -> key.startsWith("d:"));
            questDay = day;
        }
        if (!week.equals(questWeek)) {
            quests.keySet().removeIf(key -> key.startsWith("w:"));
            questWeek = week;
        }
    }

    /** As read from a file: the day and the week the progress is of. */
    void setQuestPeriod(String day, String week) {
        questDay = day == null ? "" : day;
        questWeek = week == null ? "" : week;
    }

    int questProgress(String key) {
        Integer progress = quests.get(key);
        return progress == null ? 0 : progress;
    }

    void setQuestProgress(String key, int progress) {
        if (progress <= 0) {
            quests.remove(key);
        } else {
            quests.put(key, progress);
        }
    }

    Map<String, Integer> quests() {
        return Collections.unmodifiableMap(quests);
    }

    /** The stats of this mode, or null if the player never played it. */
    ModeStats mode(String modeId) {
        return modes.get(modeId);
    }

    /** The final kills of the player, every mode together. */
    int finalKills() {
        int total = 0;
        for (ModeStats mode : modes.values()) {
            total += mode.finalKills;
        }
        return total;
    }

    /** The beds the player broke, every mode together. */
    int beds() {
        int total = 0;
        for (ModeStats mode : modes.values()) {
            total += mode.beds;
        }
        return total;
    }

    ModeStats modeOrNew(String modeId) {
        ModeStats stats = modes.get(modeId);
        if (stats == null) {
            stats = new ModeStats();
            modes.put(modeId, stats);
        }
        return stats;
    }

    Map<String, ModeStats> modes() {
        return Collections.unmodifiableMap(modes);
    }

    /** The seasons that ended, the oldest first. */
    Map<Integer, SeasonResult> seasons() {
        return seasons;
    }

    /** Wins of one mode, or of every mode when modeId is null. */
    int wins(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().wins(period, day, week);
            }
        }
        return total;
    }

    int games(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().games(period, day, week);
            }
        }
        return total;
    }

    /** The longest run of wins, in one mode or in any. */
    int best(StatsPeriod period, String modeId, String day, String week) {
        int best = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                best = Math.max(best, entry.getValue().best(period, day, week));
            }
        }
        return best;
    }

    /** Kills of one mode, or of every mode when modeId is null. */
    int kills(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().kills(period, day, week);
            }
        }
        return total;
    }

    int deaths() {
        int total = 0;
        for (ModeStats stats : modes.values()) {
            total += stats.deaths;
        }
        return total;
    }

    int losses() {
        int total = 0;
        for (ModeStats stats : modes.values()) {
            total += stats.losses;
        }
        return total;
    }
}
