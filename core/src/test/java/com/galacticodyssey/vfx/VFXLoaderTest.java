package com.galacticodyssey.vfx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.vfx.VFXEnums.BlendMode;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXLoader;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VFXLoaderTest {

    private static JsonValue json(String text) {
        return new JsonReader().parse(text);
    }

    @Test
    void parsesIdAndType() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"smoke_puff\",\"type\":\"BILLBOARD\",\"sprite\":\"smoke\"}"));
        assertEquals("smoke_puff", def.id);
        assertEquals("BILLBOARD", def.type);
        assertEquals("smoke", def.sprite);
    }

    @Test
    void defaultsAppliedWhenFieldsMissing() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(json("{\"id\":\"minimal\"}"));
        assertEquals("BILLBOARD", def.type);
        assertEquals("smoke", def.sprite);
        assertEquals(16, def.maxParticles);
        assertEquals(BlendMode.ADDITIVE, def.blendMode);
    }

    @Test
    void parsesMeshType() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"sparks\",\"type\":\"MESH\",\"mesh\":\"spark_line\",\"bounce\":0.5}"));
        assertEquals("MESH", def.type);
        assertEquals("spark_line", def.mesh);
        assertEquals(0.5f, def.bounce, 0.001f);
    }

    @Test
    void parsesBlendModeNormal() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"x\",\"blendMode\":\"NORMAL\"}"));
        assertEquals(BlendMode.NORMAL, def.blendMode);
    }

    @Test
    void parseEventBindings() {
        VFXEventBindings bindings = new VFXEventBindings();
        VFXLoader.parseBindings(bindings,
            json("{\"WeaponFiredEvent\":\"muzzle_flash_ballistic\",\"HitscanHitEvent:ENERGY\":\"impact_energy\"}"));
        assertEquals("muzzle_flash_ballistic", bindings.resolve("WeaponFiredEvent", null));
        assertEquals("impact_energy", bindings.resolve("HitscanHitEvent", "ENERGY"));
    }

    @Test
    void parsesImpactSparksResource() throws Exception {
        InputStream is = VFXLoaderTest.class.getClassLoader()
            .getResourceAsStream("data/vfx/impact_sparks.json");
        assertNotNull(is, "data/vfx/impact_sparks.json not found on classpath");
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        ParticleEffectDefinition def = VFXLoader.parseEffect(new JsonReader().parse(text));
        assertEquals("impact_sparks", def.id);
        assertTrue(def.burstCount > 0);
        assertEquals("flash", def.sprite);
    }
}
