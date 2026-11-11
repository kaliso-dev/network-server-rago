package gg.rago.kbrecorder;

import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

/** Starts and stops recordings for the command, the auto-start and the game's shutdown. */
final class RecorderControl {

    private final Recorder recorder;
    private final Minecraft mc;

    RecorderControl(Recorder recorder, Minecraft mc) {
        this.recorder = recorder;
        this.mc = mc;
    }

    Recorder recorder() {
        return recorder;
    }

    Minecraft minecraft() {
        return mc;
    }

    File start(String label) throws IOException {
        Session session = mc.getSession();
        return recorder.start(label, session.getUsername(), session.getPlayerID(), ClientSampler.server(mc),
                KbRecorderMod.VERSION);
    }

    File stop() throws IOException, InterruptedException {
        return recorder.stop();
    }

    /** Closes the file when the game quits, so that its last lines are not lost. */
    void stopQuietly() {
        try {
            recorder.stop();
        } catch (IOException e) {
            // The game is closing: nothing more can be done about a disk error.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
