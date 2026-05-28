package com.galacticodyssey.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AssetManifestTest {

    private static JsonValue parseResource(String resourcePath) throws Exception {
        InputStream is = AssetManifestTest.class.getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Resource not found: " + resourcePath);
        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return new JsonReader().parse(json);
    }

    @Test
    void parsesCharacterManifest() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        assertEquals(AssetCategory.CHARACTER, manifest.getCategory());
        assertFalse(manifest.getEntries().isEmpty());
    }

    @Test
    void entryHasIdAndPath() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry first = manifest.getEntries().get(0);
        assertEquals("human_player", first.id());
        assertTrue(first.path().endsWith(".glb"));
    }

    @Test
    void lodMidPathParsed() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry first = manifest.getEntries().get(0);
        assertNotNull(first.lodMidPath());
    }

    @Test
    void findByIdReturnsCorrectEntry() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry entry = manifest.findById("human_player");
        assertNotNull(entry);
        assertEquals("human_player", entry.id());
    }

    @Test
    void findByIdReturnsNullForUnknownId() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        assertNull(manifest.findById("does_not_exist"));
    }

    @Test
    void parsesPropsManifestWithTags() throws Exception {
        JsonValue root = parseResource("data/assets/props.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry console = manifest.findById("console_nav");
        assertNotNull(console);
        assertTrue(console.tags().contains("CONSOLE"));
        assertTrue(console.tags().contains("TECH"));
    }
}
