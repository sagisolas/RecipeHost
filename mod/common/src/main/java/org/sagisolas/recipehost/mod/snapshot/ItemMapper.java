package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks {@code BuiltInRegistries.ITEM} once and produces the three parallel
 * views the snapshot stream needs: wire {@link org.sagisolas.recipehost.protocol.Item}s,
 * their ids (in the same order, for the icon stream), and the set of namespaces
 * that contributed at least one item (one input to the {@link ModInfos} list).
 */
final class ItemMapper {

    private ItemMapper() {}

    static Result map() {
        List<org.sagisolas.recipehost.protocol.Item> wireItems = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Set<String> namespacesWithItems = new HashSet<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            Item item = entry.getValue();
            String id = key.toString();
            wireItems.add(new org.sagisolas.recipehost.protocol.Item.Builder()
                    .id(id)
                    .display_name(I18n.get(item.getDescriptionId()))
                    .build());
            ids.add(id);
            namespacesWithItems.add(key.getNamespace());
        }
        return new Result(
                Collections.unmodifiableList(wireItems),
                Collections.unmodifiableList(ids),
                Collections.unmodifiableSet(namespacesWithItems));
    }

    record Result(
            List<org.sagisolas.recipehost.protocol.Item> wireItems,
            List<String> ids,
            Set<String> namespacesWithItems) {}
}
