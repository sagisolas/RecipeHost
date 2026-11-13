/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.recipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;
import org.sagisolas.recipehost.mod.snapshot.DirectRenderThread;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeSlot;
import org.sagisolas.recipehost.protocol.RecipeType;
import org.sagisolas.recipehost.protocol.Slot;

public class RecipeAssemblyTest {

    private final RecipeAssembly assembly = new RecipeAssembly(new DirectRenderThread());

    @Test
    public void aRecipeWithALayoutCarriesWhatEachOfItsSlotsShows() {
        Slot planks = slotOf("minecraft:oak_planks");
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(candidate(
                "minecraft:crafting_shaped",
                () -> recipe("minecraft:stick"),
                read -> Optional.of(
                        new LaidOut(job("minecraft:crafting_shaped", read.getId(), 2), List.of(planks, planks))))));

        assertEquals(List.of(planks, planks), assembled.recipes().get(0).getLayoutStacksList());
        assertEquals(List.of("minecraft:stick"), assembled.layouts().get(0).recipeIds());
    }

    @Test
    public void aRecipeThatFailsToReadIsLeftOutAndTheOthersShip() {
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(
                candidate(
                        "broken:machine",
                        () -> {
                            throw new IllegalStateException("a mod's broken recipe");
                        },
                        noLayout()),
                candidate("minecraft:smelting", () -> recipe("minecraft:glass"), noLayout())));

        assertEquals(List.of("minecraft:glass"), idsOf(assembled));
    }

    @Test
    public void aRepeatedIdShipsOnlyTheFirstRecipe() {
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(
                candidate(
                        "create:mixing",
                        () -> recipe("create:brass").toBuilder()
                                .addResults(stack("create:brass_ingot"))
                                .build(),
                        noLayout()),
                candidate("create:mixing", () -> recipe("create:brass"), noLayout())));

        assertEquals(1, assembled.recipes().size());
        assertEquals(1, assembled.recipes().get(0).getResultsCount());
    }

    @Test
    public void tagListingsAndFuelAreNotRecipes() {
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(
                candidate("emi:tag", () -> recipe("emi:tag_a"), noLayout()),
                candidate("emi:fuel", () -> recipe("emi:fuel_a"), noLayout()),
                candidate("minecraft:tag_recipes/item", () -> recipe("minecraft:tag_b"), noLayout()),
                candidate("minecraft:tag_recipes/fluid", () -> recipe("minecraft:tag_c"), noLayout()),
                candidate("create:tag_recipes/item", () -> recipe("create:kept"), noLayout())));

        assertEquals(List.of("create:kept"), idsOf(assembled));
    }

    @Test
    public void theNamespacesOfTheRecipeIdsOwnTheRecipes() {
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(
                candidate("minecraft:crafting", () -> recipe("minecraft:stick"), noLayout()),
                candidate("minecraft:crafting", () -> recipe("mydatapack:cheap_saddle"), noLayout())));

        assertEquals(
                Set.of("minecraft", "mydatapack"),
                assembly.read(List.of(
                                candidate("minecraft:crafting", () -> recipe("minecraft:stick"), noLayout()),
                                candidate("minecraft:crafting", () -> recipe("mydatapack:cheap_saddle"), noLayout())))
                        .namespaces());
    }

    @Test
    public void aLayoutThatFailsLeavesItsRecipeWithoutOne() {
        RecipeAssembly.LaidOutRecipes assembled = assembled(List.of(
                candidate("broken:machine", () -> recipe("broken:a"), read -> {
                    throw new LinkageError("built against another API");
                }),
                candidate(
                        "broken:machine",
                        () -> recipe("broken:b"),
                        // Three Recipe slots, but only two stacks: the entries wouldn't line up.
                        read -> Optional.of(new LaidOut(
                                job("broken:machine", read.getId(), 3),
                                List.of(slotOf("minecraft:a"), slotOf("minecraft:b")))))));

        assertEquals(List.of("broken:a", "broken:b"), idsOf(assembled));
        assertTrue(assembled.recipes().stream().allMatch(r -> r.getLayoutStacksCount() == 0));
        assertTrue(assembled.layouts().isEmpty());
    }

    @Test
    public void readingLaysNothingOutAndCountsWhatShips() {
        List<String> laidOut = new java.util.ArrayList<>();
        List<RecipeCandidate> candidates = List.of(
                candidate("minecraft:crafting", () -> recipe("minecraft:a"), read -> {
                    laidOut.add(read.getId());
                    return Optional.empty();
                }),
                candidate("emi:tag", () -> recipe("emi:t"), noLayout()),
                candidate("minecraft:crafting", () -> recipe("minecraft:b"), read -> {
                    laidOut.add(read.getId());
                    return Optional.empty();
                }));

        RecipeAssembly.Prepared prepared = assembly.read(candidates);
        assertEquals(2, prepared.count());
        assertEquals(List.of(), laidOut);

        prepared.layOut(1, 2);
        assertEquals(List.of("minecraft:b"), laidOut);
    }

    @Test
    public void eachShippedTypeIsNamedOnceByItsFirstRecipeThatHasAName() {
        RecipeAssembly.Prepared prepared = assembly.read(List.of(
                named(candidate("minecraft:crafting", () -> recipe("minecraft:stick"), noLayout()), "Crafting"),
                named(candidate("minecraft:crafting", () -> recipe("minecraft:torch"), noLayout()), "Other"),
                candidate("create:mixing", () -> recipe("create:brass"), noLayout()),
                named(candidate("emi:tag", () -> recipe("emi:tag_a"), noLayout()), "Tags"),
                named(
                        candidate(
                                "broken:machine",
                                () -> {
                                    throw new IllegalStateException("a mod's broken recipe");
                                },
                                noLayout()),
                        "Broken"),
                named(candidate("minecraft:smelting", () -> recipe("minecraft:glass"), noLayout()), "Furnace")));

        assertEquals(
                List.of(type("minecraft:crafting", "Crafting"), type("minecraft:smelting", "Furnace")),
                prepared.recipeTypes());
    }

    @Test
    public void aTypeWhoseNameFailsIsLeftUnnamed() {
        RecipeCandidate failing = new RecipeCandidate() {
            @Override
            public String typeId() {
                return "broken:machine";
            }

            @Override
            public Recipe read() {
                return recipe("broken:a");
            }

            @Override
            public Optional<LaidOut> layOut(Recipe read) {
                return Optional.empty();
            }

            @Override
            public Optional<String> typeName() {
                throw new LinkageError("built against another API");
            }
        };

        RecipeAssembly.Prepared prepared = assembly.read(List.of(failing));

        assertEquals(1, prepared.count());
        assertEquals(List.of(), prepared.recipeTypes());
    }

    private static RecipeType type(String id, String name) {
        return RecipeType.newBuilder().setId(id).setName(name).build();
    }

    /** {@code candidate} with its type named {@code name}. */
    private static RecipeCandidate named(RecipeCandidate candidate, String name) {
        return new RecipeCandidate() {
            @Override
            public String typeId() {
                return candidate.typeId();
            }

            @Override
            public Recipe read() {
                return candidate.read();
            }

            @Override
            public Optional<LaidOut> layOut(Recipe read) {
                return candidate.layOut(read);
            }

            @Override
            public Optional<String> typeName() {
                return Optional.of(name);
            }
        };
    }

    private RecipeAssembly.LaidOutRecipes assembled(List<RecipeCandidate> candidates) {
        RecipeAssembly.Prepared prepared = assembly.read(candidates);
        return prepared.layOut(0, prepared.count());
    }

    private static List<String> idsOf(RecipeAssembly.LaidOutRecipes assembled) {
        return assembled.recipes().stream().map(Recipe::getId).toList();
    }

    private static Function<Recipe, Optional<LaidOut>> noLayout() {
        return read -> Optional.empty();
    }

    private static RecipeCandidate candidate(
            String typeId, Supplier<Recipe> read, Function<Recipe, Optional<LaidOut>> layOut) {
        return new RecipeCandidate() {
            @Override
            public String typeId() {
                return typeId;
            }

            @Override
            public Recipe read() {
                return read.get();
            }

            @Override
            public Optional<LaidOut> layOut(Recipe recipe) {
                return layOut.apply(recipe);
            }
        };
    }

    private static Recipe recipe(String id) {
        return Recipe.newBuilder().setId(id).setTypeId("minecraft:crafting").build();
    }

    private static LayoutJob job(String typeId, String recipeId, int slots) {
        List<RecipeSlot> recipeSlots = java.util.stream.IntStream.range(0, slots)
                .mapToObj(i -> RecipeSlot.newBuilder()
                        .setX(18 * i)
                        .setWidth(18)
                        .setHeight(18)
                        .build())
                .toList();
        return LayoutJob.hashed(typeId, 100, 50, recipeSlots, List.of(recipeId), (graphics, x, y) -> {});
    }

    private static ItemStack stack(String itemId) {
        return ItemStack.newBuilder().setItemId(itemId).setCount(1).build();
    }

    private static Slot slotOf(String itemId) {
        return Slot.newBuilder().addStacks(stack(itemId)).build();
    }
}
