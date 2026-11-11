package gg.rago.lobby;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
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
    private final GiftRequests gifts = new GiftRequests();

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

    /** "§c[ADMIN] §cSofiene", "§d[PREMIUM§f+§d] §5So§dfi§fene", or "§7Sofiene" for a player without a rank. */
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
            return "You need " + String.format(java.util.Locale.US, "%,d", price - profile.points()) + " more Rago Points.";
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
                + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", rank.price()) + " Rago Points" + ChatColor.GREEN + "!");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        lobby.announce(ChatColor.GOLD + "✪ " + displayName(player.getUniqueId(), player.getName()) + ChatColor.GOLD
                + " bought the rank " + rank.prefix().trim() + ChatColor.GOLD + " in the shop!");
    }

    /**
     * Gives a rank to another player, paid with the giver's Rago Points: its gift price (the ranks of
     * the web store too, prix-cadeau), and its level for the giver; the one who gets it, nothing. The
     * lobby hears it in a big message, with fireworks.
     */
    @SuppressWarnings("deprecation") // Player#sendTitle is marked "API subject to change" in 1.8.8
    boolean giftRank(Player giver, PlayerStats target, RankDef rank) {
        if (!canGift(giver, target, rank)) {
            return false;
        }
        int price = rank.giftPrice();
        PlayerStats mine = of(giver);
        mine.addPoints(-price);
        changed(mine);
        target.boughtRanks().add(rank.id());
        changed(target);
        // the names as they are now: the one who got it already wears the rank
        String from = displayName(giver.getUniqueId(), giver.getName());
        String to = displayName(target.id(), target.name());
        giver.sendMessage(ChatColor.GREEN + "You gifted the rank " + rank.prefix().trim() + ChatColor.GREEN + " to "
                + to + ChatColor.GREEN + " for " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", price) + " Rago Points" + ChatColor.GREEN + "!");
        giver.playSound(giver.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        Player got = lobby.getServer().getPlayer(target.id());
        if (got != null) {
            got.sendMessage(ChatColor.GOLD + "✪ " + from + ChatColor.GOLD + " gifted you the rank " + rank.prefix().trim()
                    + ChatColor.GOLD + "!");
            got.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "RANK GIFT!", rank.prefix().trim() + ChatColor.YELLOW + " from "
                    + ChatColor.stripColor(from));
            got.playSound(got.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        }
        lobby.celebrateGift(giver, got, giftLines(from, to, rank.prefix().trim()), ChatColor.GOLD + "✦ " + from + ChatColor.GOLD
                + " gifted the rank " + rank.prefix().trim() + ChatColor.GOLD + " to " + to + ChatColor.GOLD + "!");
        return true;
    }

    /** True if the giver may gift this rank to this player now; else the giver is told why not. */
    private boolean canGift(Player giver, PlayerStats target, RankDef rank) {
        if (!giver.hasPermission(GiftRequests.PERMISSION)) {
            giver.sendMessage(ChatColor.RED + "Gifting a rank is for " + ChatColor.LIGHT_PURPLE + "PREMIUM" + ChatColor.RED
                    + " and above.");
            return false;
        }
        if (!rank.giftable()) {
            giver.sendMessage(ChatColor.RED + "This rank cannot be gifted with Rago Points.");
            return false;
        }
        if (target.id().equals(giver.getUniqueId())) {
            giver.sendMessage(ChatColor.RED + "For yourself, buy it in the shop: /shop");
            return false;
        }
        if (owns(target, rank)) {
            giver.sendMessage(ChatColor.RED + target.name() + " already has " + rank.prefix().trim() + ChatColor.RED + ".");
            return false;
        }
        String why = cannotBuy(giver, rank.giftPrice(), rank.level());
        if (why != null) {
            giver.sendMessage(ChatColor.RED + why);
            return false;
        }
        return true;
    }

    /**
     * Offers a rank to a player online: a book opens on their screen ("[PREMIUM] Sofiene wants to
     * give you [VIP]!", YES or NO), the same buttons in their chat; a minute to answer. The giver
     * pays at YES (the rank's gift price), nothing otherwise.
     */
    void offerGift(Player giver, PlayerStats target, RankDef rank) {
        if (!canGift(giver, target, rank)) {
            return;
        }
        Player got = lobby.getServer().getPlayer(target.id());
        if (got == null) {
            giver.sendMessage(ChatColor.RED + target.name() + " must be online to accept your gift.");
            return;
        }
        gifts.offer(got.getUniqueId(), giver.getUniqueId(), rank.id(), System.currentTimeMillis());
        RankDef giverRank = rank(giver.getUniqueId());
        String plus = names.plusColour(giver.getUniqueId());
        String prefix = giverRank == null ? "" : giverRank.prefix(plus);
        String from = prefix + org.bukkit.ChatColor.getLastColors(prefix.isEmpty() ? ChatColor.GRAY.toString() : prefix)
                + giver.getName();
        String gift = rank.prefix().trim();
        giver.closeInventory();
        giver.sendMessage(ChatColor.GREEN + "You offered " + gift + ChatColor.GREEN + " to " + got.getName()
                + ": they have one minute to accept it. You pay " + ChatColor.GOLD
                + String.format(java.util.Locale.US, "%,d", rank.giftPrice()) + " Rago Points" + ChatColor.GREEN + " if they do.");
        got.sendMessage(ChatColor.GOLD + "✪ " + from + ChatColor.GOLD + " wants to give you " + gift + ChatColor.GOLD + "! Accept it?");
        // Clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + got.getName() + " " + GiftRequests.buttons(giver.getName()));
        got.playSound(got.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
        got.closeInventory();
        Books.open(lobby, got, "Rank gift", Collections.singletonList(GiftRequests.page(from, giver.getName(), gift)));
    }

    /** The answer of the player offered a rank: YES gives it (the giver pays), NO tells the giver. */
    void answerGift(Player target, String giverName, boolean yes) {
        Player giver = Bukkit.getPlayerExact(giverName);
        String rankId = giver == null ? null : gifts.take(target.getUniqueId(), giver.getUniqueId(), System.currentTimeMillis());
        RankDef rank = rankId == null ? null : server.ranks().get(rankId);
        target.closeInventory();
        if (rank == null) {
            target.sendMessage(ChatColor.RED + "No gift waiting from " + giverName + ".");
            return;
        }
        if (!yes) {
            target.sendMessage(ChatColor.GRAY + "You refused the gift of " + giver.getName() + ".");
            giver.sendMessage(ChatColor.RED + target.getName() + " refused your gift of " + rank.prefix().trim() + ChatColor.RED + ".");
            return;
        }
        if (!giftRank(giver, of(target), rank)) {
            target.sendMessage(ChatColor.RED + "The gift of " + giver.getName() + " could not be made.");
        }
    }

    /** A player left: the gifts offered to them and theirs go. */
    void forgetGifts(UUID player) {
        gifts.forget(player);
    }

    /** The big message of a gifted rank, in the middle of the chat. */
    static List<String> giftLines(String giver, String target, String rank) {
        String bar = ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "-----------------------------------------------------";
        return Arrays.asList(bar, "",
                LobbyWelcome.centred(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ RANK GIFT ✦"),
                "",
                LobbyWelcome.centred(giver + ChatColor.YELLOW + " gifted " + rank + ChatColor.YELLOW + " to"),
                LobbyWelcome.centred(target + ChatColor.YELLOW + "!"),
                "",
                LobbyWelcome.centred(ChatColor.GRAY + "Gift one too: " + ChatColor.YELLOW + "/shop" + ChatColor.GRAY + " or "
                        + ChatColor.YELLOW + "/gift <player>"),
                bar);
    }

    /** True if the player owns this cosmetic: bought, PREMIUM's with the rank, or one a rank of theirs gives (VIP's). */
    boolean owns(Player player, Cosmetic cosmetic) {
        return of(player).cosmetics().contains(cosmetic.id())
                || cosmetic.premium() && player.hasPermission(LobbyGadgets.PREMIUM_PERMISSION)
                || player.hasPermission(Cosmetic.PERMISSION + cosmetic.id());
    }

    void buyCosmetic(Player player, Cosmetic cosmetic) {
        PlayerStats profile = of(player);
        if (owns(player, cosmetic)) {
            equip(player, cosmetic);
            return;
        }
        if (cosmetic.premium()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.YELLOW + cosmetic.name() + ChatColor.GRAY
                    + " comes with the " + ChatColor.LIGHT_PURPLE + "[PREMIUM+]" + ChatColor.GRAY + " rank.");
            List<String> store = server.link("boutique");
            if (store != null) {
                for (String line : store) {
                    player.sendMessage(line);
                }
            }
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
        if (!owns(player, cosmetic)) {
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
        if (cosmetic != null && cosmetic.premium()) {
            Player online = lobby.getServer().getPlayer(player);
            return online != null && online.hasPermission(LobbyGadgets.PREMIUM_PERMISSION) ? cosmetic : null;
        }
        if (cosmetic != null && !profile.cosmetics().contains(id)) {
            // one a rank gives (VIP's): worn while the player has the rank
            Player online = lobby.getServer().getPlayer(player);
            return online != null && online.hasPermission(Cosmetic.PERMISSION + id) ? cosmetic : null;
        }
        return cosmetic;
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
        UUID id = player.getUniqueId();
        NameStyle worn = kind == NameStyle.Kind.COLOUR ? names.colour(id) : kind == NameStyle.Kind.BADGE ? names.badge(id)
                : names.plus(id);
        if (style == null || style == worn) {
            profile.choose(kind, "");
            player.sendMessage(ChatColor.GRAY + (kind == NameStyle.Kind.COLOUR ? "Your name takes the colour of your rank again."
                    : kind == NameStyle.Kind.BADGE ? "Badge taken off." : "Your + is white again."));
        } else {
            profile.choose(kind, style.id());
            player.sendMessage(ChatColor.GREEN + (kind == NameStyle.Kind.COLOUR ? "Your name now looks like this: "
                    + style.apply(player.getName()) : kind == NameStyle.Kind.BADGE ? "Your badge: " + style.text()
                    : "Your + is now " + style.plusColour() + "+" + ChatColor.GREEN + " (" + style.name() + ")"
                    + (names.rank(id).hasPlus() ? "." : ": it shows with [PREMIUM+].")));
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
                + style.apply(player.getName()) : style.describe()) + ChatColor.GREEN + ". You wear it now.");
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
            got.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "You got the " + style.describe()
                    + ChatColor.GREEN + ". You wear it now (/style to change).");
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
                        + "[PREMIUM+]" + ChatColor.RED + ".");
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
                    + "[PREMIUM+]" + ChatColor.RED + ".");
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

    /** True once the player got the starter pack (it is given once). */
    boolean ownsPack(PlayerStats profile) {
        return profile.cosmetics().contains(StarterPack.OWNED_KEY);
    }

    /**
     * /pack give, from the web store: the Rago Points, the XP boosters and the cosmetics of the
     * starter pack, once. Returns false if they had it already (nothing given again).
     */
    boolean givePack(PlayerStats profile) {
        StarterPack pack = server.starterPack();
        if (!profile.cosmetics().add(StarterPack.OWNED_KEY)) {
            return false;
        }
        if (pack.rank != null) {
            profile.boughtRanks().add(pack.rank.id());
        }
        profile.addPoints(pack.points);
        XpBoosters.setCount(profile, XpBoosters.Kind.XP, profile.boosters() + pack.boosters);
        XpBoosters.setCount(profile, XpBoosters.Kind.COINS, profile.coinBoosters() + pack.coinBoosters);
        for (String id : pack.cosmetics) {
            profile.cosmetics().add(id);
        }
        changed(profile);
        Player got = lobby.getServer().getPlayer(profile.id());
        if (got != null) {
            got.sendMessage(ChatColor.GOLD + "✪ " + ChatColor.GREEN + "Thank you! Your " + pack.name + ChatColor.GREEN + " is here:");
            for (String line : pack.contents(server.cosmetics())) {
                got.sendMessage("  " + line);
            }
            got.sendMessage(ChatColor.GRAY + "Wear your cosmetics in the " + ChatColor.GREEN + "/shop" + ChatColor.GRAY
                    + ", start a booster with " + ChatColor.AQUA + "/booster" + ChatColor.GRAY + ".");
            got.playSound(got.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
        }
        lobby.announce(ChatColor.GOLD + "✪ " + displayName(profile.id(), profile.name()) + ChatColor.GOLD + " got the "
                + pack.name + ChatColor.GOLD + "! " + ChatColor.YELLOW + "/pack");
        return true;
    }

    /** /points give or take. */
    void addPoints(PlayerStats profile, int amount) {
        profile.addPoints(amount);
        changed(profile);
    }
}
