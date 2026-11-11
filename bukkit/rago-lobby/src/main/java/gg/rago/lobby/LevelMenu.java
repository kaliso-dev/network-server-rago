package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The brewing stand of the lobby hotbar (/level): the player's Network level and how far the next
 * one is, the tier they are in and the next, the reward of every 5 levels (got, next, to come), and
 * the daily and weekly quests (/quests). Main thread only.
 */
final class LevelMenu {

    private final StatsBook book;
    private final ServerSettings server;

    LevelMenu(StatsBook book, ServerSettings server) {
        this.book = book;
        this.server = server;
    }

    void open(Player player) {
        UUID id = player.getUniqueId();
        PlayerStats stats = book.profile(id, player.getName());
        NetworkLevel levels = book.levels();
        int level = levels.level(stats.xp());
        ClickMenu menu = new ClickMenu(6, "Network Level");

        List<String> lore = new ArrayList<>();
        if (level >= NetworkLevel.MAX_LEVEL) {
            lore.add(ChatColor.GOLD + "The highest level!");
        } else {
            long into = levels.xpIntoLevel(stats.xp());
            int need = levels.xpToNext(level);
            lore.add("Progress to " + levels.tag(level + 1) + ChatColor.GRAY + ":");
            lore.add(ProfileMenu.bar(levels.progress(stats.xp())) + " " + ChatColor.AQUA + LobbyScoreboard.percent(
                    levels.progress(stats.xp())));
            lore.add(ChatColor.AQUA + String.format("%,d", into) + ChatColor.GRAY + "/" + ChatColor.AQUA + String.format("%,d",
                    need) + ChatColor.GRAY + " XP, " + ChatColor.WHITE + String.format("%,d", need - into) + ChatColor.GRAY
                    + " to go");
        }
        lore.add("");
        lore.add("Total: " + ChatColor.AQUA + String.format("%,d", stats.xp()) + " Network XP");
        double boost = book.boost(id);
        if (boost > 1.0) {
            lore.add("Your boost:" + StatsService.boostText(boost));
        }
        lore.add("");
        lore.add("Tier: " + levels.tag(tierStart(levels, level)) + ChatColor.GRAY + " (from level " + tierStart(levels, level)
                + ")");
        int nextTier = levels.nextTier(level);
        lore.add(nextTier > 0 ? "Next tier: " + levels.tag(nextTier) + ChatColor.GRAY + " at level " + nextTier
                : ChatColor.GOLD + "The last tier");
        LevelRewards.Reward next = server.levelRewards().next(Math.max(level, stats.rewardsLevel()));
        lore.add("");
        lore.add(next == null ? ChatColor.GOLD + "Every reward is yours" : "Next reward: level " + next.level());
        menu.set(4, ClickMenu.item(Material.EXP_BOTTLE, 0, ChatColor.AQUA + "Network Level " + level + " " + levels.tag(level),
                lore), null);

        List<LevelRewards.Reward> rewards = server.levelRewards().all();
        for (int i = 0; i < rewards.size() && i < 21; i++) {
            LevelRewards.Reward reward = rewards.get(i);
            menu.set(ShopMenu.slotOf(i), rewardItem(reward, level, next == reward), null);
        }

        menu.set(47, tiersItem(levels, level), null);
        menu.set(49, ClickMenu.item(Material.BOOK, 0, ChatColor.GOLD + "Quests", questSummary(stats)), (p, right) -> openQuests(p));
        List<String> xp = listOf(
                "A win: " + ChatColor.AQUA + "+" + levels.winXp() + " XP",
                "A game played: " + ChatColor.AQUA + "+" + levels.lossXp() + " XP",
                "A kill: " + ChatColor.AQUA + "+" + levels.killXp() + " XP",
                "The trainings, and the quests:",
                "daily and weekly, worth a lot.");
        List<String> boosts = boostLines(server.ranks());
        if (!boosts.isEmpty()) {
            xp.add("");
            xp.addAll(boosts);
        }
        menu.set(51, ClickMenu.item(Material.GLASS_BOTTLE, 0, ChatColor.AQUA + "How to get XP", xp), null);
        menu.open(player);
    }

    /** "PREMIUM+: +50% on everything", "VIP: +25% on everything": the ranks sold that boost the XP. */
    static List<String> boostLines(Ranks ranks) {
        List<String> lines = new ArrayList<>();
        for (RankDef rank : ranks.forSale()) {
            if (rank.xpBoost() > 1.0) {
                lines.add(rank.nameColour() + rank.displayName() + ": +" + Math.round((rank.xpBoost() - 1.0) * 100)
                        + "% on everything");
            }
        }
        return lines;
    }

    /**
     * A reward of the list: a block of gold once unlocked, a block of coal while locked (the next
     * one shines). Its name: green when got, yellow for the next one, grey for those to come.
     */
    private org.bukkit.inventory.ItemStack rewardItem(LevelRewards.Reward reward, int level, boolean next) {
        boolean got = level >= reward.level();
        List<String> lore = new ArrayList<>();
        if (reward.points() > 0) {
            lore.add(ChatColor.GOLD + "+" + String.format("%,d", reward.points()) + " Rago Points");
        }
        for (String item : reward.items()) {
            lore.add(StatsService.rewardName(server, item));
        }
        lore.add("");
        lore.add(got ? ChatColor.GREEN + "Unlocked" : next ? ChatColor.YELLOW + "Your next reward: "
                + (reward.level() - level) + " level" + (reward.level() - level > 1 ? "s" : "") + " to go"
                : ChatColor.RED + "Locked");
        String name = (got ? ChatColor.GREEN : next ? ChatColor.YELLOW : ChatColor.GRAY) + "Level " + reward.level() + " "
                + book.levels().tag(reward.level());
        org.bukkit.inventory.ItemStack item = ClickMenu.item(rewardBlock(got), 0, name, lore);
        return next ? ClickMenu.glow(item) : item;
    }

    /** Gold once unlocked, coal while locked. */
    static Material rewardBlock(boolean unlocked) {
        return unlocked ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
    }

    /** Every tier of colour, the one of the player marked. */
    private static org.bukkit.inventory.ItemStack tiersItem(NetworkLevel levels, int level) {
        List<String> lore = new ArrayList<>();
        int mine = tierStart(levels, level);
        for (Map.Entry<Integer, String> tier : levels.colours().entrySet()) {
            int first = tier.getKey();
            lore.add(levels.tag(first) + (first == mine ? ChatColor.GREEN + " ◀ you" : first < level ? ChatColor.GREEN + " ✔"
                    : ""));
        }
        return ClickMenu.item(Material.NETHER_STAR, 0, ChatColor.YELLOW + "Tiers", lore);
    }

    /** The first level of the player's tier. */
    static int tierStart(NetworkLevel levels, int level) {
        java.util.SortedMap<Integer, String> below = levels.colours().headMap(level + 1);
        return below.isEmpty() ? 1 : below.lastKey();
    }

    private List<String> questSummary(PlayerStats stats) {
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        List<String> lore = new ArrayList<>();
        for (Quests.Period period : Quests.Period.values()) {
            int done = server.quests().done(stats, period, day, week);
            int count = server.quests().of(period).size();
            lore.add(period.title() + ": " + (done == count ? ChatColor.GREEN : ChatColor.YELLOW) + done + "/" + count);
        }
        lore.add("");
        lore.add("They give a lot of Network XP.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to see them");
        return lore;
    }

    /** The quests of the day and of the week, with where the player is. */
    void openQuests(Player player) {
        PlayerStats stats = book.profile(player.getUniqueId(), player.getName());
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        ClickMenu menu = new ClickMenu(5, "Quests");
        int row = 1;
        for (Quests.Period period : Quests.Period.values()) {
            menu.set(row * 9 + 1, ClickMenu.item(period == Quests.Period.DAILY ? Material.WATCH : Material.EMPTY_MAP, 0,
                    ChatColor.GOLD + period.title(), listOf(period.resetText(now))), null);
            int slot = row * 9 + 3;
            for (Quests.Quest quest : server.quests().of(period)) {
                if (slot > row * 9 + 7) {
                    break;
                }
                int progress = Quests.progress(stats, quest, day, week);
                boolean done = progress >= quest.target();
                List<String> lore = new ArrayList<>();
                lore.add(period.title().replace(" quests", " quest"));
                lore.add("");
                lore.add(ProfileMenu.bar(progress / (float) quest.target()) + " " + ChatColor.WHITE + progress + ChatColor.GRAY
                        + "/" + quest.target());
                lore.add("");
                lore.add("Reward: " + ChatColor.AQUA + "+" + String.format("%,d", book.boosted(player.getUniqueId(), quest.xp()))
                        + " Network XP");
                lore.add("");
                lore.add(done ? ChatColor.GREEN + "Done! " + ChatColor.GRAY + period.resetText(now).replace("Resets in", "Again in")
                        : ChatColor.YELLOW + "In progress");
                menu.set(slot++, ClickMenu.item(done ? Material.ENCHANTED_BOOK : Material.PAPER, 0,
                        (done ? ChatColor.GREEN : ChatColor.YELLOW) + quest.name(), lore), null);
            }
            row += 2;
        }
        menu.set(40, ClickMenu.back("your Network level"), (p, right) -> open(p));
        menu.open(player);
    }

    private static List<String> listOf(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }
}
