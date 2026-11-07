package org.sagisolas.recipehost.mod.snapshot.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class Ids {

    private Ids() {}

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
