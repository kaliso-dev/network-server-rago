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
            "status", "profiles", "profile", "world", "mode", "debug", "sparring", "reload"));
    private static final List<String> SPARRING_OPTIONS = Collections.unmodifiableList(Arrays.asList(
            "sprint", "normal", "alterne", "stop"));
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
            case "debug":
                debug(sender);
                return true;
            case "sparring":
                sparring(sender, label, args);
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

    /** {@code /kb sparring [sprint|normal|alterne] [ticks]} or {@code /kb sparring stop}. */
    private void sparring(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Le sparring frappe le joueur qui tape la commande : lance-la en jeu.");
            return;
        }
        Player player = (Player) sender;
        String usage = "/" + label + " sparring [sprint|normal|alterne] [" + Sparring.MIN_INTERVAL_TICKS + "-"
                + Sparring.MAX_INTERVAL_TICKS + " ticks] ou /" + label + " sparring stop";
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            boolean stopped = plugin.sparring().stop(player.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + (stopped ? "Sparring arrêté." : "Pas de sparring en cours."));
            return;
        }
        if (args.length > 3) {
            usage(sender, usage);
            return;
        }
        Sparring.Pattern pattern = args.length >= 2 ? Sparring.Pattern.parse(args[1]) : Sparring.Pattern.SPRINT;
        Integer interval = args.length == 3 ? Sparring.parseInterval(args[2]) : Integer.valueOf(Sparring.DEFAULT_INTERVAL_TICKS);
        if (pattern == null || interval == null) {
            usage(sender, usage);
            return;
        }
        plugin.sparring().start(player, pattern, interval);
        String profile = plugin.settings().profileFor(player.getWorld().getName()).name();
        String hits = pattern == Sparring.Pattern.ALTERNE ? "un coup sprint puis un coup normal"
                : pattern == Sparring.Pattern.SPRINT ? "un coup sprint" : "un coup sans sprint";
        sender.sendMessage(ChatColor.GRAY + "Sparring : " + ChatColor.WHITE + hits + ChatColor.GRAY + " toutes les "
                + interval + " ticks (" + String.format(Locale.ROOT, "%.2f", interval / 20.0) + " s), profil "
                + ChatColor.WHITE + profile + ChatColor.GRAY + ". L'attaquant invisible est devant toi : regarde "
                + "dans une direction et laisse-toi pousser. /" + label + " sparring stop pour arrêter.");
        plugin.audit(sender.getName(), "sparring " + pattern.configName() + " " + interval + " ticks, profil " + profile);
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
        if (args.length == 2 && sub.equals("sparring")) {
            return matching(SPARRING_OPTIONS, args[1]);
        }
        if (args.length == 2 && sub.equals("mode")) {
            List<String> modes = new ArrayList<>();
            for (EngineMode mode : EngineMode.values()) {
                modes.add(mode.configName());
            }
            return matching(modes, args[1]);
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
