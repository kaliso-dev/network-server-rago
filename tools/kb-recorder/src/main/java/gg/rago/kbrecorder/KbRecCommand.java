package gg.rago.kbrecorder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/** The client command /kbrec: start, stop, mark and status. It runs on the player's side only. */
final class KbRecCommand extends CommandBase {

    private static final String USAGE = "/kbrec start [nom] | stop | mark [texte] | status";

    private final RecorderControl control;

    KbRecCommand(RecorderControl control) {
        this.control = control;
    }

    @Override
    public String getCommandName() {
        return "kbrec";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        String action = args.length == 0 ? "status" : args[0];
        String rest = String.join(" ", Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
        Recorder recorder = control.recorder();
        switch (action) {
            case "start":
                if (recorder.isRecording()) {
                    tell(sender, "Un enregistrement est déjà en cours : " + recorder.file().getName());
                    return;
                }
                try {
                    tell(sender, "Enregistrement démarré : " + control.start(rest).getName());
                } catch (IOException e) {
                    tell(sender, "Impossible de créer le fichier : " + e.getMessage());
                }
                return;
            case "stop":
                try {
                    File file = control.stop();
                    tell(sender, file == null ? "Aucun enregistrement en cours." : "Enregistrement arrêté : " + file.getPath());
                } catch (IOException e) {
                    tell(sender, "Erreur à l'écriture du fichier : " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            case "mark":
                if (!recorder.isRecording()) {
                    tell(sender, "Aucun enregistrement en cours : lance /kbrec start.");
                    return;
                }
                recorder.mark(rest);
                tell(sender, "Repère : " + rest);
                return;
            case "status":
                tell(sender, recorder.isRecording()
                        ? "Enregistrement en cours (" + recorder.ticks() + " ticks) : " + recorder.file().getName()
                        : "Pas d'enregistrement en cours. " + USAGE);
                return;
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private static void tell(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[KB] " + EnumChatFormatting.RESET + text));
    }
}
