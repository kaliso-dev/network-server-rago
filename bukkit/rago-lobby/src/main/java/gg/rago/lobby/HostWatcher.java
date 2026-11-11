package gg.rago.lobby;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

/** A game plugin that is disabled closes the queues of its modes. */
final class HostWatcher implements Listener {

    private final QueueController queues;

    HostWatcher(QueueController queues) {
        this.queues = queues;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        queues.unregister(event.getPlugin());
    }
}
