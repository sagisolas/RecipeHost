package org.sagisolas.recipehost.mod.snapshot.item;

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
 * Walks {@code BuiltInRegistries.ITEM} once and produces the two views the
 * snapshot stream needs: wire {@link org.sagisolas.recipehost.protocol.Item}s and the set of namespaces
 * that contributed at least one item (one input to the {@link org.sagisolas.recipehost.mod.snapshot.ModInfos} list).
 */
public final class ItemMapper {

    private ItemMapper() {}

    public static Result map() {
        List<org.sagisolas.recipehost.protocol.Item> wireItems = new ArrayList<>();
        Set<String> namespacesWithItems = new HashSet<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation key = entry.getKey().location();
            Item item = entry.getValue();
            String id = key.toString();
            wireItems.add(org.sagisolas.recipehost.protocol.Item.newBuilder()
                    .setId(id)
                    .setDisplayName(I18n.get(item.getDescriptionId()))
                    .build());
            namespacesWithItems.add(key.getNamespace());
        }
        return new Result(
                Collections.unmodifiableList(wireItems),
                Collections.unmodifiableSet(namespacesWithItems));
    }

    public record Result(
            List<org.sagisolas.recipehost.protocol.Item> wireItems,
            Set<String> namespacesWithItems) {}
}
