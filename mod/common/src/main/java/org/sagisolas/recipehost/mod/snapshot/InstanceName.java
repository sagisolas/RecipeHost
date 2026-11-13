/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Derives the {@code Instance} name sent in the {@code Handshake} from the game
 * directory (see GLOSSARY.md, ADR 0011).
 *
 * <p>TODO: verify the names on real CurseForge, Modrinth App, Prism and vanilla
 * installs with tools/probe (issue mobile-app/01). So far only the Loom dev
 * client has been checked, which reports "run".
 */
final class InstanceName {

    private InstanceName() {}

    static String of(Path gameDir, Predicate<Path> exists) {
        String folder = gameDir.getFileName().toString();
        Path parent = gameDir.getParent();
        boolean minecraftFolder = folder.equals(".minecraft") || folder.equals("minecraft");
        // Prism and MultiMC keep the game directory one level below instance.cfg.
        if (minecraftFolder
                && parent != null
                && parent.getFileName() != null
                && exists.test(parent.resolve("instance.cfg"))) {
            return parent.getFileName().toString();
        }
        // The vanilla launcher's default directory says nothing about the instance.
        if (folder.equals(".minecraft")) return "Minecraft";
        return folder;
    }
}
