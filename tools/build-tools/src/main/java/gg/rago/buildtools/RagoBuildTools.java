package gg.rago.buildtools;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoBuildTools: what a builder needs on the local test server, next to WorldEdit. Empty and flat
 * build worlds ({@code /monde}), creative mode and protection in them, flying speed and night
 * vision, and frequent saves so that a build is never lost. See the module README.
 *
 * <p>Only for the local test server: saving every world from the main thread, as this plugin does
 * every few minutes, is fine for a builder but not for a production game server.
 */
public final class RagoBuildTools extends JavaPlugin {

    private BuildWorlds worlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds = new BuildWorlds(getServer().getWorldContainer(), getLogger());
        worlds.loadAll();

        getServer().getPluginManager().registerEvents(new BuildListener(worlds), this);
        MondeCommand monde = new MondeCommand(worlds, getLogger());
        getCommand("monde").setExecutor(monde);
        getCommand("monde").setTabCompleter(monde);
        BuilderCommands builder = new BuilderCommands();
        for (String name : new String[] {"vitesse", "nv"}) {
            getCommand(name).setExecutor(builder);
            getCommand(name).setTabCompleter(builder);
        }

        int minutes = getConfig().getInt("sauvegarde-auto-minutes", 2);
        if (minutes > 0) {
            long period = minutes * 60L * 20L;
            getServer().getScheduler().runTaskTimer(this, worlds::saveAll, period, period);
            getLogger().info("Mondes de " + getServer().getWorldContainer().getAbsolutePath()
                    + ", enregistrés toutes les " + minutes + " minute(s)");
        } else {
            getLogger().info("Mondes de " + getServer().getWorldContainer().getAbsolutePath()
                    + ", sans enregistrement automatique en plus de celui de Minecraft");
        }
    }
}
