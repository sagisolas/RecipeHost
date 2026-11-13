/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.recipe;

import java.util.Optional;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Recipe type names as the game shows them, in its language (ADR 0004). */
public final class TypeNames {

    private TypeNames() {}

    /**
     * {@code name} as text, or empty when it is a translation the language has no entry for: the game
     * would show the bare key (e.g. {@code emi.category.foo.bar}), and the app's type id fallback reads better.
     */
    public static Optional<String> of(Component name) {
        if (name.getContents() instanceof TranslatableContents translatable
                && !Language.getInstance().has(translatable.getKey())) {
            return Optional.empty();
        }
        return Optional.of(name.getString());
    }
}
