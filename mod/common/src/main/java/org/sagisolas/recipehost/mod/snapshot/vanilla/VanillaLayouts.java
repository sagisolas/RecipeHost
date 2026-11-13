/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.vanilla;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.RecipeSlot;

/**
 * Draws the `Recipe layout`s of the vanilla-dispatch recipe types when EMI is
 * absent (ADR 0015). A renderer of its own, not an emulation of EMI.
 *
 * <p>Crafting, cooking and smithing layouts are the game's own container screens:
 * the screen's {@code renderBg} is drawn with an empty menu (so no items, an empty
 * arrow and an unlit flame) and cropped to the menu's own slots, leaving out the
 * player inventory. The geometry and the pixels are vanilla's, and resource packs and
 * mods that restyle those screens apply. The stonecutter's screen shows its outputs as
 * a list of buttons, not as a result slot, so its layout is drawn by hand from vanilla
 * textures.
 */
public final class VanillaLayouts {

    /** Item slots are 16 px; a slot's rectangle is its 18 px frame around that. */
    private static final int ITEM_SIZE = 16;

    private static final int SLOT_SIZE = 18;
    /** Panel around the outermost slot frames, enough to take in the 26 px result frames. */
    private static final int PADDING = 8;
    /** A large result frame reaches this far past its item on each side. */
    private static final int RESULT_FRAME_MARGIN = 5;

    private static final int NO_MOUSE = -1;
    /** The colour vanilla container labels use. */
    private static final int TEXT_COLOR = 0x404040;

    private static final int CRAFTING_RESULT = 0;
    private static final int[] CRAFTING_GRID = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int FURNACE_INGREDIENT = 0;
    private static final int FURNACE_RESULT = 2;
    private static final int[] SMITHING_INPUTS = {0, 1, 2};
    private static final int SMITHING_RESULT = 3;

    private final Font font;
    private final VanillaLayout crafting;
    private final VanillaLayout smithing;
    private final VanillaLayout stonecutter;
    private final ScreenLayout furnace;
    private final ScreenLayout blastFurnace;
    private final ScreenLayout smoker;

    /**
     * Reads each screen's slot positions from a fresh menu on the player's inventory.
     * The screens themselves are made only when a layout is drawn, on the render thread.
     */
    public VanillaLayouts(Minecraft minecraft) {
        this.font = minecraft.font;
        Inventory inventory = minecraft.player.getInventory();

        CraftingMenu craftingMenu = new CraftingMenu(0, inventory);
        this.crafting = screenLayout(
                        craftingMenu,
                        CRAFTING_GRID,
                        CRAFTING_RESULT,
                        () -> new CraftingBackground(craftingMenu, minecraft))
                .toLayout(VanillaLayoutKeys.CRAFTING);

        SmithingMenu smithingMenu = new SmithingMenu(0, inventory);
        this.smithing = screenLayout(
                        smithingMenu,
                        SMITHING_INPUTS,
                        SMITHING_RESULT,
                        () -> new SmithingBackground(smithingMenu, minecraft))
                .toLayout(VanillaLayoutKeys.SMITHING);

        FurnaceMenu furnaceMenu = new FurnaceMenu(0, inventory);
        this.furnace = screenLayout(
                furnaceMenu,
                new int[] {FURNACE_INGREDIENT},
                FURNACE_RESULT,
                () -> new FurnaceBackground(furnaceMenu, minecraft));
        BlastFurnaceMenu blastFurnaceMenu = new BlastFurnaceMenu(0, inventory);
        this.blastFurnace = screenLayout(
                blastFurnaceMenu,
                new int[] {FURNACE_INGREDIENT},
                FURNACE_RESULT,
                () -> new BlastFurnaceBackground(blastFurnaceMenu, minecraft));
        SmokerMenu smokerMenu = new SmokerMenu(0, inventory);
        this.smoker = screenLayout(
                smokerMenu,
                new int[] {FURNACE_INGREDIENT},
                FURNACE_RESULT,
                () -> new SmokerBackground(smokerMenu, minecraft));

        this.stonecutter = Stonecutter.layout();
    }

    /** Every shaped and shapeless recipe: the crafting table's 3×3 grid and result slot. */
    VanillaLayout crafting() {
        return crafting;
    }

    /**
     * A smelting, blasting, smoking or campfire recipe: its furnace's screen (the plain
     * furnace for campfires, which have none), with the cook time and XP as game-font
     * text under the result.
     */
    VanillaLayout cooking(String typeId, int cookTimeTicks, float xp) {
        ScreenLayout screen = switch (typeId) {
            case "minecraft:blasting" -> blastFurnace;
            case "minecraft:smoking" -> smoker;
            default -> furnace;
        };
        Component time = Component.translatable(
                "recipehost.layout.cooking_time", decimal(Double.toString(cookTimeTicks / 20.0)));
        Component experience = Component.translatable("recipehost.layout.experience", decimal(Float.toString(xp)));
        RecipeSlot result = screen.slots().getLast();
        int textRight = screen.width() - PADDING / 2;
        int textTop = result.getY() + SLOT_SIZE + RESULT_FRAME_MARGIN;
        LayoutJob.Drawer drawer = (graphics, mouseX, mouseY) -> {
            screen.drawer().draw(graphics, mouseX, mouseY);
            graphics.drawString(font, time, textRight - font.width(time), textTop, TEXT_COLOR, false);
            graphics.drawString(
                    font, experience, textRight - font.width(experience), textTop + font.lineHeight, TEXT_COLOR, false);
        };
        return new VanillaLayout(
                VanillaLayoutKeys.cooking(typeId, cookTimeTicks, xp),
                screen.width(),
                screen.height(),
                screen.slots(),
                drawer);
    }

    /** A smithing transform recipe: the smithing table's template, base, addition and result. */
    VanillaLayout smithing() {
        return smithing;
    }

    /** A stonecutter recipe: its input, an arrow, then the output. */
    VanillaLayout stonecutter() {
        return stonecutter;
    }

    /** A container screen's recipe area: its size, recipe slots and how to draw it, before keying. */
    private record ScreenLayout(int width, int height, List<RecipeSlot> slots, LayoutJob.Drawer drawer) {

        VanillaLayout toLayout(String key) {
            return new VanillaLayout(key, width, height, slots, drawer);
        }
    }

    /**
     * The area of {@code menu}'s screen around its own slots (every slot not in the
     * player inventory, so a furnace's fuel slot is in the picture), padded and kept
     * inside the screen. Its recipe slots are {@code inputs} in order, then {@code result}.
     */
    private static ScreenLayout screenLayout(
            AbstractContainerMenu menu, int[] inputs, int result, Supplier<Background> background) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            left = Math.min(left, slot.x - 1);
            top = Math.min(top, slot.y - 1);
            right = Math.max(right, slot.x - 1 + SLOT_SIZE);
            bottom = Math.max(bottom, slot.y - 1 + SLOT_SIZE);
        }
        int x = Math.max(0, left - PADDING);
        int y = Math.max(0, top - PADDING);
        int width = right + PADDING - x;
        int height = bottom + PADDING - y;

        List<RecipeSlot> slots = new ArrayList<>(inputs.length + 1);
        for (int index : inputs) slots.add(recipeSlot(menu.getSlot(index), x, y, RecipeSlot.Role.INPUT));
        slots.add(recipeSlot(menu.getSlot(result), x, y, RecipeSlot.Role.OUTPUT));

        LayoutJob.Drawer drawer = (graphics, mouseX, mouseY) -> {
            graphics.pose().pushPose();
            graphics.pose().translate(-x, -y, 0f);
            background.get().draw(graphics);
            graphics.pose().popPose();
        };
        return new ScreenLayout(width, height, slots, drawer);
    }

    /** The 18 px frame around a menu slot's item, relative to the area's corner at ({@code x}, {@code y}). */
    private static RecipeSlot recipeSlot(Slot slot, int x, int y, RecipeSlot.Role role) {
        return rect(slot.x - 1 - x, slot.y - 1 - y, role);
    }

    private static RecipeSlot rect(int x, int y, RecipeSlot.Role role) {
        return RecipeSlot.newBuilder()
                .setX(x)
                .setY(y)
                .setWidth(SLOT_SIZE)
                .setHeight(SLOT_SIZE)
                .setRole(role)
                .build();
    }

    /**
     * A decimal string without trailing zeros, e.g. {@code 10}, {@code 0.35}. Takes the
     * string so a float is printed as itself ({@code 0.1}), not widened to a double first.
     */
    private static String decimal(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }

    /** The stonecutter's hand-drawn layout: an input frame, an arrow and a large result frame. */
    private static final class Stonecutter {

        private static final ResourceLocation CRAFTING_TABLE =
                ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
        /** A sprite, unlike the arrows baked into container textures, so it has no panel around it. */
        private static final ResourceLocation ARROW =
                ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

        private static final int RESULT_FRAME = 26;
        private static final int INPUT_Y = (RESULT_FRAME - SLOT_SIZE) / 2;
        private static final int ARROW_X = SLOT_SIZE + 4;
        private static final int ARROW_WIDTH = 24;
        private static final int ARROW_HEIGHT = 16;
        private static final int RESULT_X = ARROW_X + ARROW_WIDTH + 4;

        static VanillaLayout layout() {
            List<RecipeSlot> slots = List.of(
                    rect(0, INPUT_Y, RecipeSlot.Role.INPUT),
                    rect(RESULT_X + RESULT_FRAME_MARGIN - 1, INPUT_Y, RecipeSlot.Role.OUTPUT));
            return new VanillaLayout(
                    VanillaLayoutKeys.STONECUTTER, RESULT_X + RESULT_FRAME, RESULT_FRAME, slots, Stonecutter::draw);
        }

        private static void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            // CraftingScreen's top-left grid cell and its result frame.
            graphics.blit(CRAFTING_TABLE, 0, INPUT_Y, 29, 16, SLOT_SIZE, SLOT_SIZE);
            graphics.blit(CRAFTING_TABLE, RESULT_X, 0, 119, 30, RESULT_FRAME, RESULT_FRAME);
            graphics.blitSprite(ARROW, ARROW_X, (RESULT_FRAME - ARROW_HEIGHT) / 2, ARROW_WIDTH, ARROW_HEIGHT);
        }
    }

    /** A container screen that draws only its background, with its top-left corner at (0, 0). */
    private interface Background {
        void draw(GuiGraphics graphics);
    }

    // The screens below are never opened or initialised. Each sets the fields its
    // renderBg reads (leftPos and topPos stay 0; some screens centre on width and
    // height instead, so those are the image size) and exposes the protected renderBg.

    private static final class CraftingBackground extends CraftingScreen implements Background {
        CraftingBackground(CraftingMenu menu, Minecraft minecraft) {
            super(menu, minecraft.player.getInventory(), Component.empty());
            this.minecraft = minecraft;
            this.font = minecraft.font;
            this.width = imageWidth;
            this.height = imageHeight;
        }

        @Override
        public void draw(GuiGraphics graphics) {
            renderBg(graphics, 0f, NO_MOUSE, NO_MOUSE);
        }
    }

    private static final class FurnaceBackground extends FurnaceScreen implements Background {
        FurnaceBackground(FurnaceMenu menu, Minecraft minecraft) {
            super(menu, minecraft.player.getInventory(), Component.empty());
            this.minecraft = minecraft;
            this.font = minecraft.font;
            this.width = imageWidth;
            this.height = imageHeight;
        }

        @Override
        public void draw(GuiGraphics graphics) {
            renderBg(graphics, 0f, NO_MOUSE, NO_MOUSE);
        }
    }

    private static final class BlastFurnaceBackground extends BlastFurnaceScreen implements Background {
        BlastFurnaceBackground(BlastFurnaceMenu menu, Minecraft minecraft) {
            super(menu, minecraft.player.getInventory(), Component.empty());
            this.minecraft = minecraft;
            this.font = minecraft.font;
            this.width = imageWidth;
            this.height = imageHeight;
        }

        @Override
        public void draw(GuiGraphics graphics) {
            renderBg(graphics, 0f, NO_MOUSE, NO_MOUSE);
        }
    }

    private static final class SmokerBackground extends SmokerScreen implements Background {
        SmokerBackground(SmokerMenu menu, Minecraft minecraft) {
            super(menu, minecraft.player.getInventory(), Component.empty());
            this.minecraft = minecraft;
            this.font = minecraft.font;
            this.width = imageWidth;
            this.height = imageHeight;
        }

        @Override
        public void draw(GuiGraphics graphics) {
            renderBg(graphics, 0f, NO_MOUSE, NO_MOUSE);
        }
    }

    private static final class SmithingBackground extends SmithingScreen implements Background {
        SmithingBackground(SmithingMenu menu, Minecraft minecraft) {
            super(menu, minecraft.player.getInventory(), Component.empty());
            this.minecraft = minecraft;
            this.font = minecraft.font;
            this.width = imageWidth;
            this.height = imageHeight;
            // Makes the armor stand preview its renderBg draws (cropped away, but it must exist).
            subInit();
        }

        @Override
        public void draw(GuiGraphics graphics) {
            renderBg(graphics, 0f, NO_MOUSE, NO_MOUSE);
        }
    }
}
