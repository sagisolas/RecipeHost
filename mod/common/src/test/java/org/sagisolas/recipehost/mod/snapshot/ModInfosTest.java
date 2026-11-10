package org.sagisolas.recipehost.mod.snapshot;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.sagisolas.recipehost.mod.ModDescriptor;
import org.sagisolas.recipehost.protocol.ModInfo;

public class ModInfosTest {

    @Test
    public void namespaceWithALoadedModGetsThatModsMetadata() {
        List<ModInfo> infos =
                ModInfos.assemble(List.of(new ModDescriptor("create", "Create", "0.5.1")), Set.of("create"));

        assertEquals(List.of(info("create", "Create", "0.5.1")), infos);
    }

    @Test
    public void namespaceWithNoLoadedModIsListedAsADatapack() {
        List<ModInfo> infos = ModInfos.assemble(List.of(), Set.of("mypack"));

        assertEquals(List.of(info("mypack", "mypack", "datapack")), infos);
    }

    @Test
    public void modThatOwnsNothingIsLeftOut() {
        List<ModInfo> infos = ModInfos.assemble(List.of(new ModDescriptor("sodium", "Sodium", "0.6.0")), Set.of());

        assertEquals(List.of(), infos);
    }

    @Test
    public void loadedModsComeInLoaderOrderThenDatapacksAlphabetically() {
        List<ModInfo> infos = ModInfos.assemble(
                List.of(
                        new ModDescriptor("minecraft", "Minecraft", "1.21.1"),
                        new ModDescriptor("sodium", "Sodium", "0.6.0"),
                        new ModDescriptor("create", "Create", "0.5.1")),
                Set.of("zpack", "create", "apack", "minecraft"));

        assertEquals(
                List.of(
                        info("minecraft", "Minecraft", "1.21.1"),
                        info("create", "Create", "0.5.1"),
                        info("apack", "apack", "datapack"),
                        info("zpack", "zpack", "datapack")),
                infos);
    }

    private static ModInfo info(String id, String name, String version) {
        return ModInfo.newBuilder().setId(id).setName(name).setVersion(version).build();
    }
}
