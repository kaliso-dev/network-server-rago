package gg.rago.combat.engine;

import gg.rago.combat.model.KnockbackProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /kb}: staff controls for the combat engine. Changes made with this command last until
 * the next restart or {@code /kb reload}; edit config.yml to keep them.
 */
final class KbCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList(
            "status", "profiles", "profile", "world", "mode", "hitdelay", "debug", "reload"));
    private static final String DEFAULT_WORLD_PROFILE = "default";

    private final RagoCombatEngine plugin;

    KbCommand(RagoCombatEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                status(sender);
                return true;
            case "profiles":
                profiles(sender);
                return true;
            case "profile":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " profile <nom>");
                }
                return change(sender, "profil par défaut -> " + args[1],
                        () -> plugin.settings().withDefaultProfile(args[1]));
            case "world":
                if (args.length != 3) {
                    return usage(sender, "/" + label + " world <monde> <profil|default>");
                }
                String profile = DEFAULT_WORLD_PROFILE.equalsIgnoreCase(args[2]) ? null : args[2];
                return change(sender, "monde " + args[1] + " -> " + (profile == null ? "profil par défaut" : profile),
                        () -> plugin.settings().withWorldProfile(args[1], profile));
            case "mode":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " mode <override|observe>");
                }
                return change(sender, "mode -> " + args[1].toLowerCase(Locale.ROOT),
                        () -> plugin.settings().withMode(EngineMode.parse(args[1])));
            case "hitdelay":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " hitdelay <ticks> (vanilla " + EngineSettings.VANILLA_HIT_DELAY_TICKS
                            + ")");
                }
                return change(sender, "délai entre deux coups -> " + args[1] + " ticks",
                        () -> plugin.settings().withHitDelayTicks(parseTicks(args[1])));
            case "debug":
                debug(sender);
                return true;
            case "reload":
                reload(sender);
                return true;
            default:
                return usage(sender, "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">");
        }
    }

    private void status(CommandSender sender) {
        EngineSettings settings = plugin.settings();
        sender.sendMessage(ChatColor.RED + "RagoCombatEngine " + ChatColor.GRAY + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Mode : " + ChatColor.WHITE + settings.mode().configName());
        sender.sendMessage(ChatColor.GRAY + "Profil par défaut : " + ChatColor.WHITE + settings.defaultProfile());
        for (Map.Entry<String, String> entry : settings.worldProfiles().entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  monde " + entry.getKey() + " : " + ChatColor.WHITE + entry.getValue());
        }
        sender.sendMessage(ChatColor.GRAY + "Délai entre deux coups : " + ChatColor.WHITE + settings.hitDelayTicks()
                + " ticks" + ChatColor.GRAY + " (vanilla " + EngineSettings.VANILLA_HIT_DELAY_TICKS + ")");
        sender.sendMessage(ChatColor.GRAY + "Journal des hits : " + ChatColor.WHITE
                + (settings.recordHits() ? "actif (" + plugin.droppedRecords() + " perdus)" : "inactif"));
    }

    private void profiles(CommandSender sender) {
        EngineSettings settings = plugin.settings();
        for (KnockbackProfile profile : settings.profiles().values()) {
            String marker = profile.name().equals(settings.defaultProfile()) ? ChatColor.RED + "* " : "  ";
            sender.sendMessage(marker + ChatColor.WHITE + profile.name() + ChatColor.GRAY
                    + " friction=" + profile.friction()
                    + " horizontal=" + profile.horizontal()
                    + " vertical=" + profile.vertical()
                    + " vertical-limit=" + profile.verticalLimit()
                    + " extra-horizontal=" + profile.extraHorizontal()
                    + " extra-vertical=" + profile.extraVertical());
        }
    }

    private boolean change(CommandSender sender, String description, SettingsChange change) {
        try {
            plugin.updateSettings(change.apply());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }
        plugin.audit(sender.getName(), description);
        sender.sendMessage(ChatColor.GRAY + "OK : " + ChatColor.WHITE + description
                + ChatColor.GRAY + " (jusqu'au prochain redémarrage ou /kb reload)");
        return true;
    }

    private void debug(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Le debug s'affiche en jeu : lance la commande depuis un joueur.");
            return;
        }
        boolean enabled = plugin.toggleDebug(((Player) sender).getUniqueId());
        sender.sendMessage(ChatColor.GRAY + "Debug KB : " + ChatColor.WHITE + (enabled ? "activé" : "désactivé"));
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadSettings();
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "config.yml invalide, réglages actuels conservés : " + e.getMessage());
            return;
        }
        plugin.audit(sender.getName(), "config.yml rechargé");
        sender.sendMessage(ChatColor.GRAY + "config.yml rechargé.");
    }

    private static int parseTicks(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("nombre de ticks invalide : " + text);
        }
    }

    private static boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(ChatColor.RED + "Usage : " + usage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("profile")) {
            return matching(new ArrayList<>(plugin.settings().profiles().keySet()), args[1]);
        }
        if (args.length == 2 && sub.equals("mode")) {
            List<String> modes = new ArrayList<>();
            for (EngineMode mode : EngineMode.values()) {
                modes.add(mode.configName());
            }
            return matching(modes, args[1]);
        }
        if (args.length == 2 && sub.equals("hitdelay")) {
            return matching(Arrays.asList("9", String.valueOf(EngineSettings.VANILLA_HIT_DELAY_TICKS)), args[1]);
        }
        if (args.length == 2 && sub.equals("world")) {
            List<String> worlds = new ArrayList<>();
            for (World world : plugin.getServer().getWorlds()) {
                worlds.add(world.getName());
            }
            return matching(worlds, args[1]);
        }
        if (args.length == 3 && sub.equals("world")) {
            List<String> options = new ArrayList<>(plugin.settings().profiles().keySet());
            options.add(DEFAULT_WORLD_PROFILE);
            return matching(options, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> matching(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface SettingsChange {
        EngineSettings apply();
    }
}
