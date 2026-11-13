/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.sagisolas.recipehost.mod.ModDescriptor;
import org.sagisolas.recipehost.protocol.ModInfo;

/**
 * Builds the {@code Handshake}'s {@link ModInfo} list: one entry per namespace
 * that owns an Item or a Recipe (see GLOSSARY.md, ADR 0011).
 */
final class ModInfos {

    static final String DATAPACK_VERSION = "datapack";

    private ModInfos() {}

    static List<ModInfo> assemble(List<ModDescriptor> loadedMods, Set<String> ownerNamespaces) {
        List<ModInfo> infos = new ArrayList<>();
        Set<String> unclaimed = new TreeSet<>(ownerNamespaces);
        for (ModDescriptor m : loadedMods) {
            if (unclaimed.remove(m.id())) {
                infos.add(info(m.id(), m.name(), m.version()));
            }
        }
        // Whatever no loaded mod claims came from a datapack.
        for (String ns : unclaimed) {
            infos.add(info(ns, ns, DATAPACK_VERSION));
        }
        return infos;
    }

    private static ModInfo info(String id, String name, String version) {
        return ModInfo.newBuilder().setId(id).setName(name).setVersion(version).build();
    }
}
