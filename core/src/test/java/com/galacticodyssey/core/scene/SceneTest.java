package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    @Test
    void sceneStartsUnloadedWithNoAssetsOrInteriorWorld() {
        Scene s = new Scene(5, SceneType.ORBITAL, new double[]{10.0, 20.0, 30.0});
        assertEquals(5, s.id);
        assertEquals(SceneType.ORBITAL, s.type);
        assertEquals(SceneState.UNLOADED, s.state);
        assertEquals(0, s.assets.size);
        assertNull(s.interiorWorld);
        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, s.galaxyAnchor, 1e-9);
    }

    @Test
    void requestExposesTargetAndAnchor() {
        SceneTransitionRequest r = new SceneTransitionRequest(SceneType.PLANET_SURFACE, new double[]{1, 2, 3});
        assertEquals(SceneType.PLANET_SURFACE, r.targetType);
        assertArrayEquals(new double[]{1, 2, 3}, r.galaxyAnchor, 1e-9);
    }

    @Test
    void phaseEnumStartsAtIdle() {
        assertEquals(TransitionPhase.IDLE, TransitionPhase.values()[0]);
    }
}
