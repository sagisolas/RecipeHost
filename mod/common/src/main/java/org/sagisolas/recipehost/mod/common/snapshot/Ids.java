package org.sagisolas.recipehost.mod.common.snapshot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

final class Ids {

    private Ids() {}

    static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
