package gg.rago.lobby;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

/**
 * What a player owns beyond their stats: their ranks and the permissions they give, their Rago
 * Points, the cosmetics they bought and equipped, their daily reward. Buying in the shop goes
 * through here. Every change is written to the player's stats file. Main thread only.
 */
final class Profiles {

    private final RagoLobby lobby;
    private final ServerSettings server;
    private final StatsService stats;
    private final NetworkRanks chat;
    private final NameLooks names;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    Profiles(RagoLobby lobby, ServerSettings server, StatsService stats, NetworkRanks chat, NameLooks names) {
        this.lobby = lobby;
        this.server = server;
        this.stats = stats;
        this.chat = chat;
        this.names = names;
    }

    NameLooks names() {
        return names;
    }

    ServerSettings server() {
        return server;
    }

    /** The player's profile, created if they have none yet. */
    PlayerStats of(Player player) {
        return stats.book().profile(player.getUniqueId(), player.getName());
    }

    /** The rank a player shows. */
    RankDef rank(UUID player) {
        return names.rank(player);
    }

    /** "§c[ADMIN] §cSofiene", "§d[PREMIUM] §5So§dfi§fene", or "§7Sofiene" for a player without a rank. */
    String displayName(UUID player, String name) {
        return names.display(player, name);
    }

    /** True if the player owns this rank or a higher one that inherits it. */
    boolean owns(PlayerStats profile, RankDef rank) {
        return profile != null && server.ranks().has(profile.rank(), profile.boughtRanks(), rank.id());
    }

    /** The player's permissions and the look of their name follow their ranks. */
    void apply(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException e) {
                // already gone with a previous session of the player
            }
        }
        PlayerStats profile = stats.book().get(player.getUniqueId());
        Set<String> permissions = profile == null ? Collections.<String>emptySet()
                : server.ranks().permissions(profile.rank(), profile.boughtRanks());
        if (!permissions.isEmpty()) {
            PermissionAttachment attachment = player.addAttachment(lobby);
            for (String permission : permissions) {
                if (permission.startsWith("-")) {
                    attachment.setPermission(permission.substring(1), false);
                } else {
                    attachment.setPermission(permission, true);
                }
            }
            attachments.put(player.getUniqueId(), attachment);
        }
        chat.refresh(player);
    }

    void forget(UUID player) {
        attachments.remove(player);
    }

    /** The profile changed: written, and the player's permissions, name and sidebar follow. */
    void changed(PlayerStats profile) {
        stats.save(profile);
        Player player = lobby.getServer().getPlayer(profile.id());
        if (player != null) {
            apply(player);
        }
        lobby.refreshBoards();
    }

    // --- The shop -------------------------------------------------------------------------------

    /** Why the player cannot buy this yet, or null if they can. */
    String cannotBuy(Player player, int price, int level) {
        PlayerStats profile = of(player);
        int own = stats.book().level(player.getUniqueId());
        if (own < level) {
            return "Reach Network level " + level + " first (you are level " + own + ").";
        }
        if (profile.points() < price) {
            return "You need " + String.format("%,d", price - profile.points()) + " more Rago Points.";
        }
        return null;
    }

    void buyRank(Player player, RankDef rank) {
        PlayerStats profile = of(player);
        if (!rank.sold()) {
            player.sendMessage(ChatColor.RED + "This rank is not sold in the shop.");
            return;
        }
        if (owns(profile, rank)) {
            player.sendMessage(ChatColor.RED + "You already have " + rank.prefix().trim() + ChatColor.RED + ".");
            return;
        }
        String why = cannotBuy(player, rank.price(), rank.level());
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        profile.addPoints(-rank.price());
        profile.boughtRanks().add(rank.id());
        changed(profile);
        player.sendMessage(ChatColor.GREEN + "You bought the rank " + rank.prefix().trim() + ChatColor.GREEN + " for "
                + ChatColor.GOLD + String.format("%,d", rank.price()) + " Rago Points" + ChatColor.GREEN + "!");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        lobby.announce(ChatColor.GOLD + "✪ " + displayName(player.getUniqueId(), player.getName()) + ChatColor.GOLD
                + " bought the rank " + rank.prefix().trim() + ChatColor.GOLD + " in the shop!");
    }

    /**
     * Gives a rank of the shop to another player, paid with the giver's Rago Points: the giver needs
     * its level and its price, as to buy it for themselves; the one who gets it, nothing.
     */
    void giftRank(Player giver, PlayerStats target, RankDef rank) {
        if (!rank.sold()) {
            giver.sendMessage(ChatColor.RED + "This rank is not sold in the shop.");
            return;
        }
        if (target.id().equals(giver.getUniqueId())) {
            giver.sendMessage(ChatColor.RED + "For yourself, buy it in the shop: /shop");
            return;
        }
        if (owns(target, rank)) {
            giver.sendMessage(ChatColor.RED + target.name() + " already has " + rank.prefix().trim() + ChatColor.RED + ".");
            return;
        }
        String why = cannotBuy(giver, rank.price(), rank.level());
        if (why != null) {
            giver.sendMessage(ChatColor.RED + why);
            return;
        }
        PlayerStats mine = of(giver);
        mine.addPoints(-rank.price());
        changed(mine);
        target.boughtRanks().add(rank.id());
        changed(target);
        giver.sendMessage(ChatColor.GREEN + "You gifted the rank " + rank.prefix().trim() + ChatColor.GREEN + " to "
                + displayName(target.id(), target.name()) + ChatColor.GREEN + " for " + ChatColor.GOLD
                + String.format("%,d", rank.price()) + " Rago Points" + ChatColor.GREEN + "!");
        giver.playSound(giver.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        Player got = lobby.getServer().getPlayer(target.id());
        if (got != null) {
            got.sendMessage(ChatColor.GOLD + "✪ " + displayName(giver.getUniqueId(), giver.getName()) + ChatColor.GOLD
                    + " gifted you the rank " + rank.prefix().trim() + ChatColor.GOLD + "!");
            got.playSound(got.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        }
        lobby.announce(ChatColor.GOLD + "✪ " + displayName(giver.getUniqueId(), giver.getName()) + ChatColor.GOLD
                + " gifted the rank " + rank.prefix().trim() + ChatColor.GOLD + " to " + displayName(target.id(), target.name())
                + ChatColor.GOLD + "!");
    }

    void buyCosmetic(Player player, Cosmetic cosmetic) {
        PlayerStats profile = of(player);
        if (profile.cosmetics().contains(cosmetic.id())) {
            equip(player, cosmetic);
            return;
        }
        String why = cannotBuy(player, cosmetic.price(), cosmetic.level());
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        profile.addPoints(-cosmetic.price());
        profile.cosmetics().add(cosmetic.id());
        profile.equip(cosmetic.type(), cosmetic.id());
        changed(profile);
        player.sendMessage(ChatColor.GREEN + "You bought " + ChatColor.YELLOW + cosmetic.name() + ChatColor.GREEN
                + " and equipped it.");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
    }

    /** Equips a cosmetic the player owns, or takes it off if it is the one they wear. */
    void equip(Player player, Cosmetic cosmetic) {
        PlayerStats profile = of(player);
        if (!profile.cosmetics().contains(cosmetic.id())) {
            return;
        }
        if (cosmetic.id().equals(profile.equipped(cosmetic.type()))) {
            profile.equip(cosmetic.type(), null);
            player.sendMessage(ChatColor.YELLOW + cosmetic.name() + ChatColor.GRAY + " taken off.");
        } else {
            profile.equip(cosmetic.type(), cosmetic.id());
            player.sendMessage(ChatColor.GREEN + "Equipped: " + ChatColor.YELLOW + cosmetic.name());
        }
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
        changed(profile);
    }

    /** The cosmetic of this type the player wears, or null. */
    Cosmetic equipped(UUID player, Cosmetic.Type type) {
        PlayerStats profile = stats.book().get(player);
        String id = profile == null ? null : profile.equipped(type);
        Cosmetic cosmetic = id == null ? null : server.cosmetics().get(id);
        return cosmetic != null && profile.cosmetics().contains(id) ? cosmetic : null;
    }

    // --- Colours of name and badges -------------------------------------------------------------

    /**
     * Wears a colour or a badge the player owns, or takes it off if they wear it (null: the plain
     * colour of the rank, no badge).
     */
    void wear(Player player, NameStyle.Kind kind, NameStyle style) {
        PlayerStats profile = of(player);
        if (style != null && !names.owns(profile, style)) {
            return;
        }
        NameStyle worn = kind == NameStyle.Kind.COLOUR ? names.colour(player.getUniqueId()) : names.badge(player.getUniqueId());
        if (style == null || style == worn) {
            profile.choose(kind, "");
            player.sendMessage(ChatColor.GRAY + (kind == NameStyle.Kind.COLOUR ? "Your name takes the colour of your rank again."
                    : "Badge taken off."));
        } else {
            profile.choose(kind, style.id());
            player.sendMessage(ChatColor.GREEN + (kind == NameStyle.Kind.COLOUR ? "Your name now looks like this: "
                    + style.apply(player.getName()) : "Your badge: " + style.text()));
        }
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
        changed(profile);
    }

    /** A colour or a badge bought with Rago Points in /style: worn at once. */
    void buyStyle(Player player, NameStyle style) {
        PlayerStats profile = of(player);
        if (style.obtain() != NameStyle.Obtain.POINTS || profile.cosmetics().contains(style.key())) {
            return;
        }
        String why = cannotBuy(player, style.price(), 0);
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        profile.addPoints(-style.price());
        profile.cosmetics().add(style.key());
        profile.choose(style.kind(), style.id());
        changed(profile);
        player.sendMessage(ChatColor.GREEN + "You bought the " + (style.kind() == NameStyle.Kind.COLOUR ? "colour of name "
                + style.apply(player.getName()) : "badge " + style.text()) + ChatColor.GREEN + ". You wear it now.");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
    }

    /** /style give: the store sold it, or the staff gives it. Returns false if they had it. */
    boolean giveStyle(PlayerStats profile, NameStyle style) {
        if (!profile.cosmetics().add(style.key())) {
            return false;
        }
        profile.choose(style.kind(), style.id());
        changed(profile);
        Player got = lobby.getServer().getPlayer(profile.id());
        if (got != null) {
            got.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "You got the "
                    + (style.kind() == NameStyle.Kind.COLOUR ? "colour of name " + style.apply(style.name())
                    : "badge " + style.text()) + ChatColor.GREEN + ". You wear it now (/style to change).");
            got.playSound(got.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
        }
        return true;
    }

    /** /style take. Returns false if they did not have it. */
    boolean takeStyle(PlayerStats profile, NameStyle style) {
        if (!profile.cosmetics().remove(style.key())) {
            return false;
        }
        changed(profile);
        return true;
    }

    // --- The line above the name, the nick ------------------------------------------------------

    /** What the player shows above their name. */
    void setHeadLine(Player player, HeadLines.Shown shown) {
        PlayerStats profile = of(player);
        if (shown == HeadLines.Shown.MESSAGE) {
            if (!player.hasPermission("rago.perk.message")) {
                player.sendMessage(ChatColor.RED + "Your own message above your name comes with " + ChatColor.LIGHT_PURPLE
                        + "[PREMIUM]" + ChatColor.RED + ".");
                return;
            }
            if (profile.headMessage() == null) {
                player.sendMessage(ChatColor.YELLOW + "Write it first: /display message <text> (" + HeadLines.MAX_MESSAGE
                        + " characters, & for the colours)");
                return;
            }
        }
        profile.setHeadLine(shown.id());
        stats.save(profile);
        player.sendMessage(ChatColor.GREEN + "Above your name: " + ChatColor.YELLOW + shown.title() + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }

    /** PREMIUM: the message above the name, which it then shows. */
    void setHeadMessage(Player player, String typed) {
        if (!player.hasPermission("rago.perk.message")) {
            player.sendMessage(ChatColor.RED + "Your own message above your name comes with " + ChatColor.LIGHT_PURPLE
                    + "[PREMIUM]" + ChatColor.RED + ".");
            return;
        }
        String message = HeadLines.cleanMessage(typed);
        if (ChatColor.stripColor(message).trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Usage: /display message <text>");
            return;
        }
        PlayerStats profile = of(player);
        profile.setHeadMessage(message);
        profile.setHeadLine(HeadLines.Shown.MESSAGE.id());
        stats.save(profile);
        player.sendMessage(ChatColor.GREEN + "Above your name: " + ChatColor.RESET + message);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }

    /**
     * PREMIUM: plays under another name (null: their own). Not the name of a player of the server,
     * nor a nick someone wears.
     */
    void nick(Player player, Nicks nicks, String nick) {
        PlayerStats profile = of(player);
        if (nick == null) {
            if (profile.nick() == null) {
                player.sendMessage(ChatColor.GRAY + "You play under your own name.");
                return;
            }
            profile.setNick(null);
            stats.save(profile);
            nicks.set(player, null);
            player.sendMessage(ChatColor.GREEN + "You play under your own name again.");
            lobby.refreshBoards();
            return;
        }
        if (!Nicks.validName(nick)) {
            player.sendMessage(ChatColor.RED + "A nick is 3 to 16 letters, digits or _.");
            return;
        }
        PlayerStats known = stats.book().byName(nick);
        Player wearer = nicks.byNick(nick);
        if ((known != null && !known.id().equals(player.getUniqueId())) || lobby.getServer().getPlayerExact(nick) != null
                || (wearer != null && wearer != player)) {
            player.sendMessage(ChatColor.RED + "Someone plays under that name: choose another one.");
            return;
        }
        if (nick.equalsIgnoreCase(player.getName())) {
            nick(player, nicks, null);
            return;
        }
        profile.setNick(nick);
        stats.save(profile);
        nicks.set(player, nick);
        player.sendMessage(ChatColor.GREEN + "You now play as " + ChatColor.WHITE + nick + ChatColor.GREEN
                + ": the others see a new player, without your rank, badge or skin. " + ChatColor.GRAY + "/nick reset to stop.");
        lobby.refreshBoards();
    }

    // --- Daily reward ---------------------------------------------------------------------------

    /**
     * The streak after claiming today: 1 for a first day or after a missed one, one more after a
     * claim yesterday; 0 if already claimed today.
     */
    static int nextStreak(String lastDay, int streak, String today, String yesterday) {
        if (today.equals(lastDay)) {
            return 0;
        }
        return yesterday.equals(lastDay) ? streak + 1 : 1;
    }

    /** True if the daily reward is waiting for the player. */
    boolean dailyReady(UUID player) {
        PlayerStats profile = stats.book().get(player);
        return profile == null || !StatsPeriod.dayKey(System.currentTimeMillis()).equals(profile.dailyDay());
    }

    void claimDaily(Player player) {
        long now = System.currentTimeMillis();
        PlayerStats profile = of(player);
        int streak = nextStreak(profile.dailyDay(), profile.dailyStreak(), StatsPeriod.dayKey(now),
                StatsPeriod.previousDayKey(now));
        if (streak == 0) {
            player.sendMessage(ChatColor.RED + "You already claimed today's reward. "
                    + StatsPeriod.DAY.resetText(now).replace("Resets in", "Come back in") + ".");
            return;
        }
        int reward = server.points().dailyReward(streak);
        profile.setDaily(StatsPeriod.dayKey(now), streak);
        profile.addPoints(reward);
        changed(profile);
        player.sendMessage(ChatColor.GREEN + "Daily reward: " + ChatColor.GOLD + "+" + reward + " Rago Points"
                + ChatColor.GRAY + " (day " + streak + " in a row"
                + (streak < ServerSettings.Points.MAX_STREAK ? ", come back tomorrow for more)" : ")"));
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
    }

    // --- Staff ----------------------------------------------------------------------------------

    /** /rank set: the rank the staff gives (null: none). */
    void setRank(PlayerStats profile, RankDef rank) {
        profile.setRank(rank == null || rank == Ranks.DEFAULT ? null : rank.id());
        changed(profile);
    }

    /** /points give or take. */
    void addPoints(PlayerStats profile, int amount) {
        profile.addPoints(amount);
        changed(profile);
    }
}
