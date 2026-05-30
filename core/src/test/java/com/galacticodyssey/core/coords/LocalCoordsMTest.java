package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalCoordsMTest {
    @Test
    void toVector3PreservesComponents() {
        LocalCoordsM l = new LocalCoordsM(1.5f, -2.0f, 3.25f);
        Vector3 v = l.toVector3();
        assertEquals(1.5f, v.x, 0f);
        assertEquals(-2.0f, v.y, 0f);
        assertEquals(3.25f, v.z, 0f);
    }
}
