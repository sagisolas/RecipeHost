package org.sagisolas.recipehost.mod;

public final class RecipeHost {

    public static final String MOD_ID = "recipehost";

    private RecipeHost() {}

    public static void init() {
        // Inventory button + world-leave hooks are wired in the inventory-button slice
        // (.scratch/qr-pairing/issues/03-inventory-button.md).
    }
}
