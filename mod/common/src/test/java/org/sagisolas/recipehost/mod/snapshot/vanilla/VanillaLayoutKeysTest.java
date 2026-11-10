package org.sagisolas.recipehost.mod.snapshot.vanilla;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VanillaLayoutKeysTest {

    @Test
    public void aCookingKeyNamesItsTypeCookTimeAndXp() {
        assertEquals("minecraft:smelting/200/0.1", VanillaLayoutKeys.cooking("minecraft:smelting", 200, 0.1f));
        assertEquals(
                "minecraft:campfire_cooking/600/0.35",
                VanillaLayoutKeys.cooking("minecraft:campfire_cooking", 600, 0.35f));
        assertEquals("minecraft:blasting/100/1.0", VanillaLayoutKeys.cooking("minecraft:blasting", 100, 1f));
    }
}
