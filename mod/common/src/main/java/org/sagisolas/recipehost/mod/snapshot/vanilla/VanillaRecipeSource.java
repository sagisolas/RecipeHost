/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.vanilla;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.recipe.LaidOut;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeCandidate;
import org.sagisolas.recipehost.protocol.Recipe;

/** The level's {@code RecipeManager}, mapped by the vanilla dispatch, with the companion mod's own layouts. */
public final class VanillaRecipeSource implements RecipeSource {

    private final ClientLevel level;

    public VanillaRecipeSource(ClientLevel level) {
        this.level = level;
    }

    @Override
    public List<RecipeCandidate> candidates() {
        RegistryAccess registryAccess = level.registryAccess();
        RecipeMapper mapper = new RecipeMapper(new VanillaLayouts(Minecraft.getInstance()));
        return level.getRecipeManager().getRecipes().stream()
                .<RecipeCandidate>map(holder -> new VanillaCandidate(holder, mapper, registryAccess))
                .toList();
    }

    /**
     * One recipe of the {@code RecipeManager}. The dispatch maps its body and layout in one pass, so
     * {@link #read()} keeps the layout for {@link #layOut}.
     */
    private static final class VanillaCandidate implements RecipeCandidate {

        private final RecipeHolder<?> holder;
        private final RecipeMapper mapper;
        private final RegistryAccess registryAccess;
        private Optional<LaidOut> layout = Optional.empty();

        VanillaCandidate(RecipeHolder<?> holder, RecipeMapper mapper, RegistryAccess registryAccess) {
            this.holder = holder;
            this.mapper = mapper;
            this.registryAccess = registryAccess;
        }

        @Override
        public String typeId() {
            return RecipeMapper.typeIdOf(holder.value());
        }

        @Override
        public Recipe read() {
            RecipeMapper.Mapped mapped = mapper.map(holder, registryAccess);
            layout = mapped.layout();
            return mapped.recipe();
        }

        @Override
        public Optional<LaidOut> layOut(Recipe read) {
            return layout;
        }

        @Override
        public Optional<String> typeName() {
            return VanillaTypeNames.of(typeId());
        }
    }
}
