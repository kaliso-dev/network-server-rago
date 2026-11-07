package gg.rago.kbrecorder;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Rago KB Recorder: records, on the client, the knockback received and the combat packets around
 * it, to measure a server's knockback. It reads only what the client already receives and sends,
 * and changes nothing in the game.
 */
@Mod(modid = KbRecorderMod.MOD_ID, name = "Rago KB Recorder", version = KbRecorderMod.VERSION,
        acceptedMinecraftVersions = "[1.8.9]")
public final class KbRecorderMod {

    static final String MOD_ID = "ragokbrecorder";
    static final String VERSION = "0.1.0";

    /** When set, a recording with this label starts at the first connection (automated tests). */
    static final String AUTOSTART_PROPERTY = "rago.kbrecorder.autostart";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!event.getSide().isClient()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Recorder recorder = new Recorder(new File(mc.mcDataDir, "rago-kb-recorder"), Clock.SYSTEM);
        RecorderControl control = new RecorderControl(recorder, mc);
        MinecraftForge.EVENT_BUS.register(new RecorderEvents(control));
        ClientCommandHandler.instance.registerCommand(new KbRecCommand(control));
        Runtime.getRuntime().addShutdownHook(new Thread(control::stopQuietly, "Rago KB recorder shutdown"));
    }
}
