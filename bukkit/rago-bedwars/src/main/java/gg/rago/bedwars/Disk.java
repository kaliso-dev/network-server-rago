package gg.rago.bedwars;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Files of the plugin and of the game worlds. Away from the main thread only. */
final class Disk {

    /**
     * What a game world does not take from its map: the identity of the world (two loaded worlds
     * cannot share it), the lock of the server that has it open, the players' data, the settings of
     * RagoBuildTools (the copy is not a build world) and the Bed Wars points (the copy is not a map).
     */
    static final Set<String> NOT_COPIED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "uid.dat", "session.lock", "playerdata", "stats", "rago-monde.yml", BedwarsMap.FILE_NAME)));

    private Disk() {
    }

    /** Writes the file through a .part file, so that a crash leaves the old one whole. */
    static void write(File file, String text) throws IOException {
        File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("cannot create " + folder);
        }
        File part = new File(file.getPath() + ".part");
        Files.write(part.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Copies a world folder, without {@link #NOT_COPIED} at its top. */
    static void copyWorld(File from, File to) throws IOException {
        final Path source = from.toPath();
        final Path target = to.toPath();
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                if (!dir.equals(source) && dir.getParent().equals(source)
                        && NOT_COPIED.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (file.getParent().equals(source) && NOT_COPIED.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Deletes a folder and everything in it. Nothing if it does not exist. */
    static void delete(File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }
        Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
