package org.sagisolas.recipehost.mod.snapshot;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Derives the {@code Instance} name sent in the {@code Handshake} from the game
 * directory (see GLOSSARY.md, ADR 0011).
 */
final class InstanceName {

    private InstanceName() {}

    static String of(Path gameDir, Predicate<Path> exists) {
        String folder = gameDir.getFileName().toString();
        Path parent = gameDir.getParent();
        boolean minecraftFolder = folder.equals(".minecraft") || folder.equals("minecraft");
        // Prism and MultiMC keep the game directory one level below instance.cfg.
        if (minecraftFolder && parent != null && parent.getFileName() != null
                && exists.test(parent.resolve("instance.cfg"))) {
            return parent.getFileName().toString();
        }
        // The vanilla launcher's default directory says nothing about the instance.
        if (folder.equals(".minecraft")) return "Minecraft";
        return folder;
    }
}
