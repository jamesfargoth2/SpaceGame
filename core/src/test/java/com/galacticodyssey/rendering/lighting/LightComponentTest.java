package com.galacticodyssey.rendering.lighting;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightComponentTest {

    @Test
    void defaultsToPointLight() {
        LightComponent light = new LightComponent();
        assertEquals(LightComponent.Type.POINT, light.type);
    }

    @Test
    void defaultColorIsWhite() {
        LightComponent light = new LightComponent();
        assertEquals(Color.WHITE, light.color);
    }

    @Test
    void defaultRadiusIsTen() {
        LightComponent light = new LightComponent();
        assertEquals(10f, light.radius, 0.001f);
    }

    @Test
    void defaultIntensityIsOne() {
        LightComponent light = new LightComponent();
        assertEquals(1f, light.intensity, 0.001f);
    }
}
