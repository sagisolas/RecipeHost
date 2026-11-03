package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;

public class InstanceNameTest {

    private static final Predicate<Path> NOTHING_EXISTS = p -> false;

    @Test
    public void curseForgeInstanceIsNamedAfterItsFolder() {
        Path dir = Path.of("/Users/me/curseforge/minecraft/Instances/ATM9");
        assertEquals("ATM9", InstanceName.of(dir, NOTHING_EXISTS));
    }

    @Test
    public void modrinthProfileIsNamedAfterItsFolder() {
        Path dir = Path.of("/Users/me/Library/Application Support/ModrinthApp/profiles/Fabulously Optimized");
        assertEquals("Fabulously Optimized", InstanceName.of(dir, NOTHING_EXISTS));
    }

    @Test
    public void prismInstanceIsNamedAfterTheFolderHoldingInstanceCfg() {
        Path dir = Path.of("/Users/me/PrismLauncher/instances/foo/.minecraft");
        Predicate<Path> exists = Set.of(Path.of("/Users/me/PrismLauncher/instances/foo/instance.cfg"))::contains;
        assertEquals("foo", InstanceName.of(dir, exists));
    }

    @Test
    public void multiMcInstanceIsNamedAfterTheFolderHoldingInstanceCfg() {
        Path dir = Path.of("/Users/me/MultiMC/instances/bar/minecraft");
        Predicate<Path> exists = Set.of(Path.of("/Users/me/MultiMC/instances/bar/instance.cfg"))::contains;
        assertEquals("bar", InstanceName.of(dir, exists));
    }

    @Test
    public void vanillaDotMinecraftIsCalledMinecraft() {
        Path dir = Path.of("/Users/me/AppData/Roaming/.minecraft");
        assertEquals("Minecraft", InstanceName.of(dir, NOTHING_EXISTS));
    }

    @Test
    public void customGameDirectoryIsNamedAfterItself() {
        Path dir = Path.of("/Users/me/games/mc-1.21-testing");
        assertEquals("mc-1.21-testing", InstanceName.of(dir, NOTHING_EXISTS));
    }
}
