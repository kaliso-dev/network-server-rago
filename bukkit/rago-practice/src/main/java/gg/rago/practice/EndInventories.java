package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * The [View] of the recap of a duel: what a player had at its end (or when they went out of it),
 * in a chest that nothing leaves: their inventory as they had it, the hotbar under the rest as in
 * their own, their armour, their health, their effects and their numbers of the recap. Kept
 * {@value #KEEP_MINUTES} minutes under a code, which the [View] of the recap opens (/matchinv
 * code). Main thread only.
 */
final class EndInventories implements CommandExecutor, Listener {

    static final int KEEP_MINUTES = 10;
    /** At most this many kept: the oldest go first. */
    static final int MAX_KEPT = 1000;
    static final int SIZE = 54;
    /** The armour, from the helmet down to the boots, from this slot. */
    static final int ARMOUR_SLOT = 45;
    static final int HEALTH_SLOT = 50;
    static final int EFFECTS_SLOT = 51;
    static final int NUMBERS_SLOT = 52;
    private static final String CODE_LETTERS = "abcdefghijkmnpqrstuvwxyz23456789";

    /** What a player had. */
    static final class Shot {
        final String name;
        /** The 36 slots of the inventory: the hotbar (0 to 8), then the rest (9 to 35). */
        final ItemStack[] contents;
        /** The boots, the leggings, the chestplate and the helmet, as Bukkit gives them. */
        final ItemStack[] armour;
        /** In half hearts: 20 for full health, 0 for one out of the fight. */
        final double health;
        final List<String> effects;
        final long at;
        /** The player's numbers of the recap, once the duel is over. */
        List<String> numbers = Collections.emptyList();

        Shot(String name, ItemStack[] contents, ItemStack[] armour, double health, List<String> effects, long at) {
            this.name = name;
            this.contents = contents;
            this.armour = armour;
            this.health = health;
            this.effects = effects;
            this.at = at;
        }
    }

    /** The chest of a kept inventory: nothing in it moves. */
    static final class View implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Map<String, Shot> kept = new LinkedHashMap<>();
    private final Random random = new Random();

    /** Keeps what this player has now, under a new code, returned. */
    String keep(Player player, String name, double health) {
        sweep(System.currentTimeMillis());
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(effect(effect));
        }
        Shot shot = new Shot(name, copy(player.getInventory().getContents(), 36), copy(player.getInventory().getArmorContents(), 4),
                health, effects, System.currentTimeMillis());
        String code;
        do {
            code = code(random);
        } while (kept.containsKey(code));
        kept.put(code, shot);
        return code;
    }

    /** The player's numbers of the recap, shown with their inventory. */
    void numbers(String code, List<String> numbers) {
        Shot shot = kept.get(code);
        if (shot != null) {
            shot.numbers = new ArrayList<>(numbers);
        }
    }

    /** The inventory kept under this code, or null (never kept, or gone). */
    Shot shot(String code) {
        sweep(System.currentTimeMillis());
        return code == null ? null : kept.get(code.toLowerCase(Locale.ROOT));
    }

    /** Those kept too long go, and the oldest beyond {@value #MAX_KEPT}. */
    private void sweep(long now) {
        Iterator<Shot> it = kept.values().iterator();
        while (it.hasNext()) {
            Shot shot = it.next();
            if (now - shot.at > KEEP_MINUTES * 60_000L || kept.size() > MAX_KEPT) {
                it.remove();
            } else {
                break;
            }
        }
    }

    /** A code of six letters and digits, without those that look alike. */
    static String code(Random random) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        }
        return code.toString();
    }

    private static ItemStack[] copy(ItemStack[] items, int size) {
        ItemStack[] copy = new ItemStack[size];
        for (int i = 0; i < size && i < items.length; i++) {
            copy[i] = items[i] == null || items[i].getType() == Material.AIR ? null : items[i].clone();
        }
        return copy;
    }

    /** "Speed II (0:12)". */
    @SuppressWarnings("deprecation") // PotionEffectType#getName: the name of the effect
    static String effect(PotionEffect effect) {
        String name = effect.getType().getName().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder text = new StringBuilder(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (effect.getAmplifier() > 0) {
            text.append(' ').append(roman(effect.getAmplifier() + 1));
        }
        int seconds = effect.getDuration() / 20;
        if (seconds < 60 * 60) {
            text.append(" (").append(PracticeGame.clock(seconds)).append(')');
        }
        return text.toString();
    }

    private static String roman(int number) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number >= 1 && number <= numerals.length ? numerals[number - 1] : String.valueOf(number);
    }

    // --- The chest ------------------------------------------------------------------------------

    /** Where a slot of the inventory (0 to 35) goes in the chest: the rest on top, the hotbar under it, as in a player's own. */
    static int chestSlot(int inventorySlot) {
        return inventorySlot < 9 ? 27 + inventorySlot : inventorySlot - 9;
    }

    /** The 54 slots of the chest of what a player had. */
    static ItemStack[] layout(Shot shot) {
        ItemStack[] slots = new ItemStack[SIZE];
        for (int i = 0; i < 36; i++) {
            slots[chestSlot(i)] = shot.contents[i];
        }
        for (int i = 36; i < 45; i++) {
            slots[i] = PracticeGame.named(Material.STAINED_GLASS_PANE, 1, 7, " ");
        }
        for (int i = 0; i < 4; i++) {
            // the helmet first: Bukkit gives the boots first
            slots[ARMOUR_SLOT + i] = shot.armour[3 - i];
        }
        slots[HEALTH_SLOT] = PracticeGame.named(Material.SPECKLED_MELON, 1, 0,
                ChatColor.RED + "Health: " + MatchRecap.hearts(shot.health) + "❤");
        List<String> effects = new ArrayList<>();
        for (String effect : shot.effects) {
            effects.add(ChatColor.GRAY + effect);
        }
        slots[EFFECTS_SLOT] = PracticeGame.named(Material.POTION, 1, 0, ChatColor.LIGHT_PURPLE + "Effects",
                effects.isEmpty() ? new String[] {ChatColor.GRAY + "None"} : effects.toArray(new String[0]));
        if (!shot.numbers.isEmpty()) {
            slots[NUMBERS_SLOT] = PracticeGame.named(Material.PAPER, 1, 0, ChatColor.GOLD + "Match recap",
                    shot.numbers.toArray(new String[0]));
        }
        return slots;
    }

    /** "Alice's inventory", 32 characters at most (a chest's title in 1.8). */
    static String title(String name) {
        String title = name + "'s inventory";
        return title.length() <= 32 ? title : title.substring(0, 32);
    }

    void open(Player viewer, Shot shot) {
        View view = new View();
        view.inventory = Bukkit.createInventory(view, SIZE, title(shot.name));
        view.inventory.setContents(layout(shot));
        viewer.openInventory(view.inventory);
    }

    /** /matchinv code: the inventory a [View] of a recap shows. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can do this.");
            return true;
        }
        Shot shot = args.length == 1 ? shot(args[0]) : null;
        if (shot == null) {
            sender.sendMessage(ChatColor.RED + "This inventory is no longer kept: the inventories of a duel stay "
                    + KEEP_MINUTES + " minutes.");
            return true;
        }
        open((Player) sender, shot);
        return true;
    }

    // --- The [View] in the chat -----------------------------------------------------------------

    /** The line, then [View]: a click opens the inventory kept under this code. */
    static void tell(Player reader, String line, String code, String name) {
        // clickable through the vanilla /tellraw, as the lobby's buttons: a player's name is letters, digits and _
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + reader.getName() + " " + json(line, code, name));
    }

    /** The JSON of the line and its [View]. */
    static String json(String line, String code, String name) {
        return "[\"\",{\"text\":\"" + escape(line) + "\"},{\"text\":\" [View]\",\"color\":\"yellow\","
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/matchinv " + code + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escape("What " + name + " had at the end") + "\"}}]";
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- Nothing moves in the chest -------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }
}
