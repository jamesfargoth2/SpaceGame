package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneComponentTest {

    @Test
    void componentDefaultsToZeroAndStoresId() {
        SceneComponent c = new SceneComponent();
        assertEquals(0, c.sceneId);
        SceneComponent c2 = new SceneComponent(7);
        assertEquals(7, c2.sceneId);
    }

    @Test
    void persistentMarkerIsAComponent() {
        PersistentSceneMemberComponent m = new PersistentSceneMemberComponent();
        assertTrue(m instanceof com.badlogic.ashley.core.Component);
    }

    @Test
    void sceneTypeAndStateHaveExpectedValues() {
        assertEquals(6, SceneType.values().length);
        assertNotNull(SceneType.valueOf("STATION_INTERIOR"));
        assertEquals(SceneState.UNLOADED, SceneState.values()[0]);
    }
}
