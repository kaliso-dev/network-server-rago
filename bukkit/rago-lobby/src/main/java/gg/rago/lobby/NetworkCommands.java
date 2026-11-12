package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * The commands of a network: the shop and the profile, friends and private messages, the
 * leaderboards in the chat, the staff's ranks, points and sanctions, the links. See the README.
 */
final class NetworkCommands implements CommandExecutor, TabCompleter {

    static final String[] NAMES = {"shop", "gift", "profile", "daily", "friend", "msg", "reply", "rank", "points", "top", "stats",
        "fly", "staffchat", "vanish", "freeze", "report", "reports", "clearchat", "mutechat", "ban", "unban", "mute", "unmute", "kick",
        "warn", "history", "staff", "discord", "rules", "site", "store", "style", "level", "quests", "display", "nick", "realname", "booster", "pack", "settings", "season",
        "titles", "spectate"};

    private static final String[] TOPS = {"elo", "level", "wins", "kills", "fkdr", "streak", "games", "points"};

    private final RagoLobby lobby;

    NetworkCommands(RagoLobby lobby) {
        this.lobby = lobby;
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        sender.sendMessage(ChatColor.RED + "Only a player can do this.");
        return null;
    }

    private static boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
        return false;
    }

    /** /season: the season now, its end, its rewards, the player's divisions and their past seasons. */
    private void season(CommandSender sender) {
        Season season = lobby.server().season();
        StatsBook book = lobby.stats().book();
        EloLadder ladder = book.ladder();
        String end = season.endText(System.currentTimeMillis());
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + season.name() + (end.isEmpty() ? "" : ChatColor.GRAY + " - " + end));
        sender.sendMessage(ChatColor.GRAY + "Every ranked ELO starts again from 0 at each season. At its end, the best division you"
                + " reached in a ranked mode gives Rago Points:");
        StringBuilder rewards = new StringBuilder();
        for (EloLadder.Division division : ladder.divisions()) {
            int points = season.reward(division.name());
            if (points > 0) {
                rewards.append(rewards.length() == 0 ? "" : ChatColor.DARK_GRAY + ", ").append(division.coloured()).append(' ')
                        .append(ChatColor.GOLD).append(String.format(java.util.Locale.US, "%,d", points));
            }
        }
        if (rewards.length() > 0) {
            sender.sendMessage(rewards.toString());
        }
        if (!(sender instanceof Player)) {
            return;
        }
        java.util.UUID id = ((Player) sender).getUniqueId();
        PlayerStats profile = book.get(id);
        List<String> mine = new ArrayList<>();
        for (GameMode mode : StatsMenu.rankedModes(lobby.settings())) {
            ModeStats stats = profile == null ? null : profile.modes().get(mode.id());
            if (stats != null && stats.games() > 0) {
                mine.add(ChatColor.GRAY + StatsMenu.shortName(mode) + " " + ladder.format(stats.elo)
                        + (stats.peakElo > stats.elo ? ChatColor.DARK_GRAY + " (best " + stats.peakElo + ")" : ""));
            }
        }
        sender.sendMessage(ChatColor.WHITE + "You: " + (mine.isEmpty() ? ChatColor.GRAY + "no ranked game this season yet"
                : String.join(ChatColor.DARK_GRAY + ", ", mine)));
        if (profile != null && !profile.seasons().isEmpty()) {
            List<String> past = new ArrayList<>();
            for (java.util.Map.Entry<Integer, PlayerStats.SeasonResult> entry : profile.seasons().entrySet()) {
                past.add(ChatColor.GRAY + "Season " + entry.getKey() + " " + ladder.format(entry.getValue().elo));
            }
            sender.sendMessage(ChatColor.WHITE + "Your seasons: " + String.join(ChatColor.DARK_GRAY + ", ", past));
        }
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, Math.min(from, args.length), args.length)).trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player;
        switch (command.getName()) {
            case "shop":
                if ((player = player(sender)) != null) {
                    lobby.shop().open(player);
                }
                return true;
            case "profile":
                if ((player = player(sender)) != null) {
                    lobby.profileMenu().open(player);
                }
                return true;
            case "season":
                season(sender);
                return true;
            case "settings":
                if ((player = player(sender)) != null) {
                    lobby.profileMenu().openSettings(player);
                }
                return true;
            case "gift":
                if ((player = player(sender)) != null) {
                    if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
                        // the YES and NO of the book of a rank offered
                        lobby.profiles().answerGift(player, args[1], args[0].equalsIgnoreCase("accept"));
                    } else if (args.length != 1) {
                        player.sendMessage(ChatColor.RED + "Usage: /gift <player>");
                    } else {
                        PlayerStats target = profileOf(player, args[0]);
                        if (target != null && target.id().equals(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "For yourself, buy it in the shop: /shop");
                        } else if (target != null) {
                            lobby.shop().openGift(player, target);
                        }
                    }
                }
                return true;
            case "daily":
                if ((player = player(sender)) != null) {
                    lobby.profiles().claimDaily(player);
                }
                return true;
            case "friend":
                if ((player = player(sender)) != null) {
                    lobby.social().command(player, args);
                }
                return true;
            case "msg":
                if ((player = player(sender)) != null) {
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /msg <player> <message>");
                    } else {
                        lobby.social().message(player, args[0], rest(args, 1));
                    }
                }
                return true;
            case "reply":
                if ((player = player(sender)) != null) {
                    if (args.length == 0) {
                        player.sendMessage(ChatColor.RED + "Usage: /r <message>");
                    } else {
                        lobby.social().reply(player, rest(args, 0));
                    }
                }
                return true;
            case "rank":
                rank(sender, args);
                return true;
            case "points":
                points(sender, args);
                return true;
            case "top":
                top(sender, args);
                return true;
            case "stats":
                stats(sender, args);
                return true;
            case "spectate":
                if ((player = player(sender)) != null) {
                    lobby.spectating().command(player, args);
                }
                return true;
            case "fly":
                if ((player = player(sender)) != null && allowed(player, "rago.perk.fly")) {
                    lobby.toggleFly(player);
                }
                return true;
            case "staffchat":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    if (args.length == 0) {
                        lobby.staff().toggleStaffChannel(player);
                    } else {
                        lobby.staff().staffChat(player, rest(args, 0));
                    }
                }
                return true;
            case "vanish":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    lobby.staff().toggleVanish(player);
                }
                return true;
            case "freeze":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    Player target = args.length == 0 ? null : Bukkit.getPlayerExact(args[0]);
                    if (target == null || target.equals(player)) {
                        player.sendMessage(ChatColor.RED + "Usage: /freeze <online player>");
                    } else {
                        lobby.staff().toggleFreeze(player, target);
                    }
                }
                return true;
            case "report":
                if ((player = player(sender)) != null) {
                    Player target = args.length == 0 ? null : lobby.social().visible(player, args[0]);
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /report <player> <reason>");
                    } else if (target == null || target.equals(player)) {
                        player.sendMessage(ChatColor.RED + "Can't find a player by the name of '" + args[0] + "'");
                    } else {
                        lobby.staff().report(player, target, rest(args, 1));
                    }
                }
                return true;
            case "reports":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    lobby.staff().reports(player);
                }
                return true;
            case "clearchat":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    lobby.staff().clearChat(player);
                }
                return true;
            case "mutechat":
                if ((player = player(sender)) != null && allowed(player, "rago.mod")) {
                    lobby.staff().toggleChatMute(player);
                }
                return true;
            case "ban":
                if (allowed(sender, "rago.mod")) {
                    lobby.punishments().sanction(sender, Punishments.Kind.BAN, args);
                }
                return true;
            case "unban":
                if (allowed(sender, "rago.mod")) {
                    lobby.punishments().lift(sender, Punishments.Kind.BAN, args);
                }
                return true;
            case "mute":
                if (allowed(sender, "rago.staff")) {
                    lobby.punishments().sanction(sender, Punishments.Kind.MUTE, args);
                }
                return true;
            case "unmute":
                if (allowed(sender, "rago.staff")) {
                    lobby.punishments().lift(sender, Punishments.Kind.MUTE, args);
                }
                return true;
            case "kick":
                if (allowed(sender, "rago.staff")) {
                    lobby.punishments().note(sender, Punishments.Kind.KICK, args);
                }
                return true;
            case "warn":
                if (allowed(sender, "rago.staff")) {
                    lobby.punishments().note(sender, Punishments.Kind.WARN, args);
                }
                return true;
            case "history":
                if (allowed(sender, "rago.staff")) {
                    lobby.punishments().history(sender, args);
                }
                return true;
            case "staff":
                if ((player = player(sender)) != null && allowed(player, "rago.staff")) {
                    if (args.length > 0 && Bukkit.getPlayerExact(args[0]) != null) {
                        lobby.staffPanel().openPlayer(player, Bukkit.getPlayerExact(args[0]).getName());
                    } else {
                        lobby.staffPanel().open(player, 0);
                    }
                }
                return true;
            case "discord":
                link(sender, "discord");
                if (sender instanceof Player && lobby.server().link("discord") != null) {
                    lobby.gettingStarted(((Player) sender).getUniqueId(), GettingStarted.Step.DISCORD);
                }
                return true;
            case "rules":
                link(sender, "regles");
                return true;
            case "site":
                link(sender, "site");
                return true;
            case "store":
                link(sender, "boutique");
                return true;
            case "style":
                style(sender, label.equalsIgnoreCase("badge") && args.length == 0 ? new String[] {"badges"} : args);
                return true;
            case "booster":
                booster(sender, args);
                return true;
            case "pack":
                pack(sender, args);
                return true;
            case "titles":
                if ((player = player(sender)) != null) {
                    if (args.length == 0) {
                        lobby.titlesMenu().open(player);
                    } else if (args[0].matches("(?i)best|meilleur|auto")) {
                        lobby.profiles().setTitle(player, null);
                    } else if (args[0].matches("(?i)none|off|" + Titles.NONE)) {
                        lobby.profiles().setTitle(player, Titles.NONE);
                    } else {
                        lobby.profiles().setTitle(player, args[0].toLowerCase(Locale.ROOT));
                    }
                }
                return true;
            case "display":
                if ((player = player(sender)) != null) {
                    if (args.length == 0) {
                        lobby.profileMenu().openDisplay(player);
                    } else if (args[0].equalsIgnoreCase("message")) {
                        lobby.profiles().setHeadMessage(player, rest(args, 1));
                    } else {
                        HeadLines.Shown shown = HeadLines.Shown.of(args[0]);
                        if (shown == null) {
                            player.sendMessage(ChatColor.RED + "Usage: /display <elo|level|wins|kills|streak|title|message|none>,"
                                    + " /display message <text>");
                        } else {
                            lobby.profiles().setHeadLine(player, shown);
                        }
                    }
                }
                return true;
            case "nick":
                if ((player = player(sender)) != null && allowed(player, "rago.perk.nick")) {
                    if (label.equalsIgnoreCase("unnick") || args.length == 1 && args[0].matches("(?i)reset|off|remove|stop|clear")) {
                        lobby.profiles().nick(player, lobby.nicks(), null);
                    } else if (args.length != 1) {
                        String nick = lobby.nicks().nick(player.getUniqueId());
                        player.sendMessage(ChatColor.GRAY + (nick == null ? "You play under your own name." : "You play as "
                                + ChatColor.WHITE + nick + ChatColor.GRAY + ".") + " " + ChatColor.YELLOW + "/nick <name>"
                                + ChatColor.GRAY + ", " + ChatColor.YELLOW + "/nick reset");
                    } else {
                        lobby.profiles().nick(player, lobby.nicks(), args[0]);
                    }
                }
                return true;
            case "realname":
                if (allowed(sender, "rago.staff")) {
                    Player nicked = args.length == 1 ? lobby.nicks().byNick(args[0]) : null;
                    sender.sendMessage(args.length != 1 ? ChatColor.RED + "Usage: /realname <nick>" : nicked == null
                            ? ChatColor.GRAY + "Nobody plays as " + args[0] + "." : ChatColor.WHITE + args[0] + ChatColor.GRAY
                                    + " is " + lobby.profiles().displayName(nicked.getUniqueId(), nicked.getName()));
                }
                return true;
            case "level":
                if ((player = player(sender)) != null) {
                    lobby.levelMenu().open(player);
                }
                return true;
            case "quests":
                if ((player = player(sender)) != null) {
                    lobby.levelMenu().openQuests(player);
                }
                return true;
            default:
                return false;
        }
    }

    // --- Colours of name and badges -------------------------------------------------------------

    /**
     * /style: the menu. The admins and the console (the web store) give and take the colours and
     * badges: /style give|take &lt;player&gt; &lt;couleur-feu|badge-rago&gt;, /style list.
     */
    private void style(CommandSender sender, String[] args) {
        ServerSettings server = lobby.server();
        if (args.length == 1 && args[0].matches("(?i)badges?")) {
            Player player = player(sender);
            if (player != null) {
                lobby.styleMenu().openBadges(player);
            }
            return;
        }
        if (args.length == 0 || !sender.hasPermission("rago.admin")) {
            Player player = player(sender);
            if (player != null) {
                lobby.styleMenu().open(player);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            for (NameStyle style : server.styles().values()) {
                sender.sendMessage(ChatColor.YELLOW + style.key() + ChatColor.GRAY + " " + style.sample() + ChatColor.GRAY
                        + " (" + style.obtain().name().toLowerCase(Locale.ROOT) + ")");
            }
            return;
        }
        if (args.length != 3 || !sub.matches("give|take")) {
            sender.sendMessage(ChatColor.RED + "Usage: /style <give|take> <player> <couleur-feu|badge-rago|plus-noir>, /style list");
            return;
        }
        NameStyle style = styleNamed(server, args[2]);
        if (style == null) {
            sender.sendMessage(ChatColor.RED + "No colour or badge " + args[2] + ". /style list");
            return;
        }
        PlayerStats target = profileOf(sender, args[1]);
        if (target == null) {
            return;
        }
        boolean done = sub.equals("give") ? lobby.profiles().giveStyle(target, style) : lobby.profiles().takeStyle(target, style);
        sender.sendMessage(done ? ChatColor.GREEN + target.name() + (sub.equals("give") ? " now owns " : " no longer owns ")
                + style.key() + "." : ChatColor.GRAY + target.name() + (sub.equals("give") ? " already owns " : " does not own ")
                + style.key() + ".");
        lobby.getLogger().info(sender.getName() + ": /style " + String.join(" ", args));
    }

    /**
     * /booster: the menu of the XP boosters; /booster on starts one. The admins and the console
     * (the web store) give them: /booster give &lt;player&gt; [count].
     */
    /** /pack: the store menu; /pack give <player>: the web store gives the starter pack (once). */
    private void pack(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("rago.admin")) {
            PlayerStats target = profileOf(sender, args[1]);
            if (target == null) {
                return;
            }
            boolean given = lobby.profiles().givePack(target);
            sender.sendMessage(given ? ChatColor.GREEN + target.name() + " got the starter pack."
                    : ChatColor.YELLOW + target.name() + " has the starter pack already: nothing given again.");
            lobby.getLogger().info(sender.getName() + ": /pack " + String.join(" ", args) + (given ? "" : " (had it already)"));
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            lobby.store().tellPack(player);
        } else {
            lobby.store().open(player);
        }
    }

    private void booster(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("rago.admin")) {
            int count = 1;
            if (args.length >= 3) {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    count = 0;
                }
            }
            XpBoosters.Kind kind = args.length >= 4 ? boosterKind(args[3]) : XpBoosters.Kind.XP;
            if (count < 1 || count > XpBoosters.MAX_STOCK || kind == null) {
                sender.sendMessage(ChatColor.RED + "Usage: /booster give <player> [1-" + XpBoosters.MAX_STOCK + "] [xp|coins|daily]");
                return;
            }
            PlayerStats target = profileOf(sender, args[1]);
            if (target == null) {
                return;
            }
            lobby.boosters().give(target, count, kind);
            sender.sendMessage(ChatColor.GREEN + target.name() + " got " + count + " " + kind.label + (count > 1 ? "s" : "") + ".");
            lobby.getLogger().info(sender.getName() + ": /booster " + String.join(" ", args));
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 1 && args[0].matches("(?i)on|start|activer")) {
            lobby.boosters().activate(player);
        } else if (args.length >= 1 && boosterKind(args[0]) == XpBoosters.Kind.COINS) {
            lobby.boosters().activate(player, XpBoosters.Kind.COINS);
        } else {
            lobby.boosters().open(player);
        }
    }

    /** The kind of booster of a word of /booster: xp, coins (pieces), daily; null for another word. */
    static XpBoosters.Kind boosterKind(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.matches("xp|exp")) {
            return XpBoosters.Kind.XP;
        }
        if (w.matches("coins?|pieces?|points?")) {
            return XpBoosters.Kind.COINS;
        }
        if (w.matches("daily|gratuit|jour")) {
            return XpBoosters.Kind.DAILY;
        }
        return null;
    }

    /** "couleur-feu", "badge-rago", "plus-noir", or the id alone when no other style shares it. */
    static NameStyle styleNamed(ServerSettings server, String typed) {
        String key = typed.toLowerCase(Locale.ROOT);
        NameStyle exact = server.styles().get(key);
        if (exact != null) {
            return exact;
        }
        NameStyle found = null;
        for (NameStyle.Kind kind : NameStyle.Kind.values()) {
            NameStyle style = server.style(kind, key);
            if (style != null) {
                if (found != null) {
                    return null;
                }
                found = style;
            }
        }
        return found;
    }

    private void link(CommandSender sender, String key) {
        List<String> lines = lobby.server().link(key);
        if (lines == null || lines.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Not set up on this server yet.");
            return;
        }
        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    // --- Ranks ----------------------------------------------------------------------------------

    private void rank(CommandSender sender, String[] args) {
        Ranks ranks = lobby.server().ranks();
        if (args.length == 0 || !sender.hasPermission("rago.admin")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                RankDef rank = lobby.profiles().rank(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Your rank: " + (rank == Ranks.DEFAULT ? ChatColor.GRAY + "Default"
                        : rank.prefix().trim()) + ChatColor.GRAY + ". Buy one with Rago Points: /shop");
            }
            if (sender.hasPermission("rago.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "/rank list" + ChatColor.GRAY + ", " + ChatColor.YELLOW
                        + "/rank set <player> <rank|default>" + ChatColor.GRAY + " (the staff rank), " + ChatColor.YELLOW
                        + "/rank give <player> <rank>" + ChatColor.GRAY + ", " + ChatColor.YELLOW + "/rank take <player> <rank>"
                        + ChatColor.GRAY + " (shop ranks)");
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            for (RankDef rank : ranks.all()) {
                sender.sendMessage(rank.prefix().trim() + ChatColor.GRAY + " " + rank.id() + (rank.staff() ? " (staff)" : "")
                        + (rank.sold() ? " - " + String.format(java.util.Locale.US, "%,d", rank.price()) + " points, level " + rank.level() : "")
                        + (rank.parent() == null ? "" : ", inherits " + rank.parent()));
            }
            return;
        }
        if (args.length < 3 || !sub.matches("set|give|take")) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank <set|give|take> <player> <rank>");
            return;
        }
        PlayerStats target = profileOf(sender, args[1]);
        if (target == null) {
            return;
        }
        RankDef rank = ranks.get(args[2].toLowerCase(Locale.ROOT));
        if (rank == null) {
            sender.sendMessage(ChatColor.RED + "No rank " + args[2] + ". /rank list");
            return;
        }
        if (sub.equals("set")) {
            lobby.profiles().setRank(target, rank);
            sender.sendMessage(ChatColor.GREEN + target.name() + "'s rank is now " + (rank == Ranks.DEFAULT ? "default"
                    : rank.prefix().trim()) + ChatColor.GREEN + ".");
        } else if (rank == Ranks.DEFAULT) {
            sender.sendMessage(ChatColor.RED + "Give or take a rank of the shop.");
        } else if (sub.equals("give")) {
            target.boughtRanks().add(rank.id());
            lobby.profiles().changed(target);
            sender.sendMessage(ChatColor.GREEN + target.name() + " now owns " + rank.prefix().trim() + ChatColor.GREEN + ".");
        } else {
            target.boughtRanks().remove(rank.id());
            lobby.profiles().changed(target);
            sender.sendMessage(ChatColor.GREEN + target.name() + " no longer owns " + rank.prefix().trim() + ChatColor.GREEN + ".");
        }
        lobby.getLogger().info(sender.getName() + ": /rank " + String.join(" ", args));
    }

    private PlayerStats profileOf(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerStats profile = online != null ? lobby.profiles().of(online) : lobby.stats().book().byName(name);
        if (profile == null) {
            sender.sendMessage(ChatColor.RED + "No player called " + name + " has ever played here.");
        }
        return profile;
    }

    // --- Points ---------------------------------------------------------------------------------

    private void points(CommandSender sender, String[] args) {
        if (args.length >= 3 && sender.hasPermission("rago.admin")) {
            PlayerStats target = profileOf(sender, args[1]);
            if (target == null) {
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + args[2] + " is not a number.");
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (amount < 0 || !sub.matches("give|take|set")) {
                sender.sendMessage(ChatColor.RED + "Usage: /points <give|take|set> <player> <amount>");
                return;
            }
            int change = sub.equals("give") ? amount : sub.equals("take") ? -amount : amount - target.points();
            lobby.profiles().addPoints(target, change);
            sender.sendMessage(ChatColor.GREEN + target.name() + " now has " + String.format(java.util.Locale.US, "%,d", target.points())
                    + " Rago Points.");
            lobby.getLogger().info(sender.getName() + ": /points " + String.join(" ", args));
            return;
        }
        PlayerStats profile = args.length == 1 && sender.hasPermission("rago.staff") ? profileOf(sender, args[0])
                : sender instanceof Player ? lobby.profiles().of((Player) sender) : null;
        if (profile == null) {
            sender.sendMessage(ChatColor.RED + "Usage: /points [player], or /points <give|take|set> <player> <amount>");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + (args.length == 1 ? profile.name() + " has " : "You have ") + ChatColor.GOLD
                + String.format(java.util.Locale.US, "%,d", profile.points()) + " Rago Points" + ChatColor.GRAY + ". Spend them in the /shop.");
    }

    // --- Leaderboards in the chat ---------------------------------------------------------------

    private void top(CommandSender sender, String[] args) {
        String what = args.length == 0 ? "elo" : args[0].toLowerCase(Locale.ROOT);
        StatsBook book = lobby.stats().book();
        List<GameMode> ranked = StatsMenu.rankedModes(lobby.settings());
        long now = System.currentTimeMillis();
        List<StatsBook.Row> rows;
        String title;
        BoardStat stat = null;
        if (what.equals("points")) {
            rows = new ArrayList<>();
            for (PlayerStats stats : book.all()) {
                if (stats.points() > 0) {
                    rows.add(new StatsBook.Row(stats.id(), stats.name(), stats.points()));
                }
            }
            Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                    : a.name().compareToIgnoreCase(b.name()));
            title = "Rago Points";
        } else {
            switch (what) {
                case "level":
                    stat = BoardStat.LEVEL;
                    break;
                case "wins":
                    stat = BoardStat.WINS;
                    break;
                case "kills":
                    stat = BoardStat.KILLS;
                    break;
                case "fkdr":
                    stat = BoardStat.FKDR;
                    break;
                case "streak":
                    stat = BoardStat.STREAK;
                    break;
                case "games":
                    stat = BoardStat.GAMES;
                    break;
                case "elo":
                    stat = BoardStat.ELO;
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Usage: /top <" + String.join("|", TOPS) + "> [ranked mode]");
                    return;
            }
            String modeId = null;
            if (stat == BoardStat.ELO) {
                GameMode mode = args.length > 1 ? lobby.mode(args[1]) : ranked.isEmpty() ? null : ranked.get(0);
                if (mode == null || !mode.ranked()) {
                    sender.sendMessage(ChatColor.RED + "Ranked modes: " + ids(ranked));
                    return;
                }
                modeId = mode.id();
                title = "Ranked ELO - " + ChatColor.stripColor(mode.name());
            } else {
                title = stat.title() + " (ever)";
            }
            rows = book.ranking(stat, StatsPeriod.ALL, modeId, now).rows();
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Top 10 - " + title);
        if (rows.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nobody yet.");
        }
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            StatsBook.Row row = rows.get(i);
            String value = stat == BoardStat.ELO ? book.ladder().format(row.value())
                    : stat == BoardStat.LEVEL ? book.levels().tag(book.levels().level(row.value()))
                    : stat == BoardStat.FKDR ? ChatColor.YELLOW + BoardStat.hundredths(row.value())
                    : ChatColor.YELLOW + String.format(java.util.Locale.US, "%,d", row.value());
            sender.sendMessage(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.RESET
                    + lobby.profiles().displayName(row.id(), row.name()) + ChatColor.GRAY + " - " + value);
        }
    }

    private static String ids(List<GameMode> modes) {
        List<String> ids = new ArrayList<>();
        for (GameMode mode : modes) {
            ids.add(mode.id());
        }
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    // --- A profile in the chat ------------------------------------------------------------------

    private void stats(CommandSender sender, String[] args) {
        PlayerStats profile = args.length > 0 ? profileOf(sender, args[0])
                : sender instanceof Player ? lobby.profiles().of((Player) sender) : null;
        if (profile == null) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /stats <player>");
            }
            return;
        }
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        int wins = profile.wins(StatsPeriod.ALL, null, day, week);
        int kills = profile.kills(StatsPeriod.ALL, null, day, week);
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------" + ChatColor.RESET + " "
                + lobby.profiles().displayName(profile.id(), profile.name()) + " " + ChatColor.GOLD + ChatColor.STRIKETHROUGH
                + "------");
        for (String line : lobby.profileMenu().summary(profile, profile.id())) {
            if (!line.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + line);
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Games played: " + ChatColor.WHITE + profile.games());
        sender.sendMessage(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + wins + ChatColor.GRAY + "  Losses: " + ChatColor.RED
                + profile.losses() + ChatColor.GRAY + "  WLR: " + ChatColor.GOLD + StatsMenu.ratio(wins, profile.losses()));
        sender.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + kills + ChatColor.GRAY + "  Deaths: " + ChatColor.RED
                + profile.deaths() + ChatColor.GRAY + "  K/D: " + ChatColor.YELLOW + StatsMenu.ratio(kills, profile.deaths()));
        sender.sendMessage(ChatColor.GRAY + "Final kills: " + ChatColor.GREEN + profile.finalKills() + ChatColor.GRAY
                + "  Final deaths: " + ChatColor.RED + profile.finalDeaths() + ChatColor.GRAY + "  FKDR: " + ChatColor.GOLD
                + StatsMenu.ratio(profile.finalKills(), profile.finalDeaths()));
    }

    // --- Completion -----------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        switch (command.getName()) {
            case "friend":
                if (args.length == 1) {
                    options.addAll(Arrays.asList("add", "accept", "deny", "remove", "list", "requests", "toggle"));
                } else if (args.length == 2) {
                    options.addAll(onlineNames(sender));
                }
                break;
            case "top":
                if (args.length == 1) {
                    options.addAll(Arrays.asList(TOPS));
                } else if (args.length == 2 && args[0].equalsIgnoreCase("elo")) {
                    for (GameMode mode : StatsMenu.rankedModes(lobby.settings())) {
                        options.add(mode.id());
                    }
                }
                break;
            case "rank":
                if (args.length == 1 && sender.hasPermission("rago.admin")) {
                    options.addAll(Arrays.asList("list", "set", "give", "take"));
                } else if (args.length == 2 && sender.hasPermission("rago.admin")) {
                    options.addAll(onlineNames(sender));
                } else if (args.length == 3 && sender.hasPermission("rago.admin")) {
                    options.add("default");
                    for (RankDef rank : lobby.server().ranks().all()) {
                        options.add(rank.id());
                    }
                }
                break;
            case "display":
                if (args.length == 1) {
                    options.addAll(Arrays.asList("elo", "level", "wins", "kills", "streak", "title", "message", "none"));
                }
                break;
            case "titles":
                if (args.length == 1) {
                    options.addAll(Arrays.asList("best", "none"));
                    for (Titles.Family family : lobby.server().titles().families()) {
                        options.add(family.key);
                    }
                }
                break;
            case "nick":
                if (args.length == 1) {
                    options.add("reset");
                }
                break;
            case "spectate":
                if (args.length == 1 && sender instanceof Player) {
                    // the players in a game whom they may watch
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (other != sender && lobby.state().isInGame(other) && !lobby.state().isWatching(other.getUniqueId())
                                && lobby.canSee((Player) sender, other)) {
                            options.add(other.getName());
                        }
                    }
                }
                break;
            case "style":
                if (args.length == 1 && sender.hasPermission("rago.admin")) {
                    options.addAll(Arrays.asList("give", "take", "list"));
                } else if (args.length == 2 && sender.hasPermission("rago.admin")) {
                    options.addAll(onlineNames(sender));
                } else if (args.length == 3 && sender.hasPermission("rago.admin")) {
                    options.addAll(lobby.server().styles().keySet());
                }
                break;
            case "points":
                if (args.length == 1 && sender.hasPermission("rago.admin")) {
                    options.addAll(Arrays.asList("give", "take", "set"));
                } else if (args.length == 2) {
                    options.addAll(onlineNames(sender));
                }
                break;
            case "ban":
            case "mute":
                if (args.length == 1) {
                    options.addAll(onlineNames(sender));
                } else if (args.length == 2) {
                    options.addAll(Arrays.asList("30m", "1h", "1d", "7d", "30d", "perm"));
                }
                break;
            default:
                if (args.length == 1) {
                    options.addAll(onlineNames(sender));
                }
                break;
        }
        List<String> matching = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                matching.add(option);
            }
        }
        return matching;
    }

    private List<String> onlineNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player) || lobby.canSee((Player) sender, player)) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
