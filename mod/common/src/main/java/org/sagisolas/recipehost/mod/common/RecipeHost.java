package org.sagisolas.recipehost.mod.common;

public final class RecipeHost {

    public static final String MOD_ID = "recipehost";

    private RecipeHost() {}

    public static void init() {
        // DEV ONLY — until the QR-screen slice lands, bind a listener on world-join.
        Platform.onWorldJoin(DevHarness::bindOrRebind);
    }
}
