package com.galacticodyssey.rendering.materials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialDataRegistryTest {

    private MaterialDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialDataRegistry();
    }

    @Test
    void loadParsesJsonArray() {
        registry.loadFromJson("[{\"name\":\"test\",\"metallicScale\":0.8}]");
        MaterialData data = registry.getData("test");
        assertNotNull(data);
        assertEquals(0.8f, data.metallicScale, 0.001f);
    }

    @Test
    void loadAppliesDefaults() {
        registry.loadFromJson("[{\"name\":\"minimal\"}]");
        MaterialData data = registry.getData("minimal");
        assertNotNull(data);
        assertEquals(1f, data.tilingX, 0.001f);
        assertEquals(1f, data.tilingY, 0.001f);
        assertEquals(1f, data.roughnessScale, 0.001f);
    }

    @Test
    void getDataReturnsNullForUnknown() {
        assertNull(registry.getData("nonexistent"));
    }

    @Test
    void registerAddsData() {
        MaterialData data = new MaterialData();
        data.name = "custom";
        data.metallicScale = 0.5f;
        registry.register(data);
        assertSame(data, registry.getData("custom"));
    }
}
