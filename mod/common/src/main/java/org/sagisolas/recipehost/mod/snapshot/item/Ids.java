/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class Ids {

    private Ids() {}

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
