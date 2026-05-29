package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class AttractionEnvelopeTest {
    @Test
    void generatesRequestedCountWithinBounds() {
        float height = 10f, radius = 3f;
        for (EnvelopeShape shape : EnvelopeShape.values()) {
            Array<Vector3> pts = AttractionEnvelope.generate(shape, height, radius, 150, new Random(1));
            assertEquals(150, pts.size, "count for " + shape);
            for (Vector3 p : pts) {
                assertTrue(p.y >= -0.01f && p.y <= height + 0.01f, shape + " y out of range: " + p.y);
                float horiz = (float) Math.sqrt(p.x * p.x + p.z * p.z);
                assertTrue(horiz <= radius + 0.01f, shape + " horiz out of range: " + horiz);
            }
        }
    }

    @Test
    void isDeterministic() {
        Array<Vector3> a = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 8f, 2f, 80, new Random(99));
        Array<Vector3> b = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 8f, 2f, 80, new Random(99));
        assertEquals(a.size, b.size);
        for (int i = 0; i < a.size; i++) assertEquals(a.get(i), b.get(i));
    }
}
