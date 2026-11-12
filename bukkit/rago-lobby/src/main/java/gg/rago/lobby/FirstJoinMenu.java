package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.Matchmaker;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

/**
 * The menu of the first arrival on the server, "What do you want to do?": a few choices, each a
 * click away from playing (premiere-venue: in config.yml: a mode, whose queue it joins, a training
 * starting at once; or a category of the compass), and under them Getting started, the quests of
 * a new player. It opens once, a moment after the welcome; Esc closes it. Main thread.
 */
final class FirstJoinMenu {

    static final String TITLE = "What do you want to do?";
    /** Getting started: the middle of the bottom row. */
    static final int STARTED_SLOT = 31;

    private final RagoLobby lobby;
    private final LobbySettings settings;
    private final QueueController queues;
    private final ModeMenu modes;

    FirstJoinMenu(RagoLobby lobby, LobbySettings settings, QueueController queues, ModeMenu modes) {
        this.lobby = lobby;
        this.settings = settings;
        this.queues = queues;
        this.modes = modes;
    }

    /** At the first arrival: the menu, if the player is still in the lobby with nothing open. */
    void openIfFree(Player player) {
        if (player.isOnline() && !settings.firstChoices().isEmpty() && lobby.state().isInLobby(player)
                && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
            open(player);
        }
    }

    void open(Player player) {
        List<FirstChoice> choices = settings.firstChoices();
        ClickMenu menu = new ClickMenu(4, TITLE);
        for (int i = 0; i < choices.size(); i++) {
            FirstChoice choice = choices.get(i);
            menu.set(ModeMenu.categorySlot(i, choices.size()), ClickMenu.item(choice.icon(), 0, choice.name(), lore(choice)),
                    (p, right) -> choose(p, choice));
        }
        List<GettingStarted.Step> steps = lobby.gettingStartedSteps();
        if (!steps.isEmpty()) {
            menu.set(STARTED_SLOT, startedItem(lobby.server(), steps), (p, right) -> lobby.levelMenu().openQuests(p));
        }
        menu.open(player);
    }

    /** What the choice holds, and what a click does. */
    private List<String> lore(FirstChoice choice) {
        List<String> lore = new ArrayList<>(choice.description());
        lore.add("");
        GameMode mode = playable(choice);
        lore.add(ChatColor.GREEN + (mode == null ? "Click to see the modes" : Matchmaker.alone(mode) ? "Click to play"
                : "Click to join the queue"));
        return lore;
    }

    /** The mode of the choice if this server plays it now, else null (its category opens instead). */
    private GameMode playable(FirstChoice choice) {
        GameMode mode = choice.modeId() == null ? null : settings.catalog().get(choice.modeId());
        return mode != null && queues.isPlayable(mode.id()) ? mode : null;
    }

    /** A click: the mode's queue (a training starts at once), else its category, or the category chosen. */
    private void choose(Player player, FirstChoice choice) {
        GameMode mode = playable(choice);
        if (mode != null) {
            player.closeInventory();
            queues.join(player, mode);
            return;
        }
        ModeDisplay display = choice.modeId() == null ? null : settings.display(choice.modeId());
        String category = display != null ? display.category() : choice.categoryId();
        if (category == null || !modes.openCategory(player, category)) {
            modes.open(player);
        }
    }

    /** Getting started: each step, and what all of them give. */
    static org.bukkit.inventory.ItemStack startedItem(ServerSettings server, List<GettingStarted.Step> steps) {
        GettingStarted quests = server.gettingStarted();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "" + steps.size() + " quests for a new player:");
        for (GettingStarted.Step step : steps) {
            lore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.WHITE + step.title());
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Reward: " + ChatColor.GOLD + quests.totalPoints(steps) + " Rago Points"
                + (quests.reward() == null ? "" : ChatColor.GRAY + ","));
        if (quests.reward() != null) {
            lore.add(ChatColor.GRAY + "and " + StatsService.rewardName(server, quests.reward()));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to see them");
        return ClickMenu.glow(ClickMenu.item(Material.BOOK, 0, ChatColor.GOLD + "" + ChatColor.BOLD + "Getting started", lore));
    }
}
