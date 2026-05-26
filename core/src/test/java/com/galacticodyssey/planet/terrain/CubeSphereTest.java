package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubeSphereTest {

    @Test
    void allFacesProduceUnitVectors() {
        for (CubeFace face : CubeFace.values()) {
            for (float u = 0f; u <= 1f; u += 0.25f) {
                for (float v = 0f; v <= 1f; v += 0.25f) {
                    Vector3 dir = CubeSphere.toSphere(face, u, v);
                    float len = dir.len();
                    assertEquals(1f, len, 1e-5f,
                        face + " u=" + u + " v=" + v + " produced vector of length " + len);
                }
            }
        }
    }

    @Test
    void faceCentersPointAlongAxis() {
        Vector3 px = CubeSphere.toSphere(CubeFace.POS_X, 0.5f, 0.5f);
        assertEquals(1f, px.x, 0.01f);
        assertEquals(0f, px.y, 0.01f);
        assertEquals(0f, px.z, 0.01f);

        Vector3 ny = CubeSphere.toSphere(CubeFace.NEG_Y, 0.5f, 0.5f);
        assertEquals(0f, ny.x, 0.01f);
        assertEquals(-1f, ny.y, 0.01f);
        assertEquals(0f, ny.z, 0.01f);
    }

    @Test
    void adjacentFacesShareEdgeVertices() {
        for (float v = 0f; v <= 1f; v += 0.1f) {
            Vector3 a = CubeSphere.toSphere(CubeFace.POS_X, 1f, v);
            Vector3 b = CubeSphere.toSphere(CubeFace.NEG_Z, 0f, v);
            assertEquals(a.x, b.x, 1e-4f, "Seam mismatch at v=" + v);
            assertEquals(a.y, b.y, 1e-4f, "Seam mismatch at v=" + v);
            assertEquals(a.z, b.z, 1e-4f, "Seam mismatch at v=" + v);
        }
    }

    @Test
    void inverseRoundTrips() {
        for (CubeFace face : CubeFace.values()) {
            for (float u = 0.1f; u <= 0.9f; u += 0.2f) {
                for (float v = 0.1f; v <= 0.9f; v += 0.2f) {
                    Vector3 dir = CubeSphere.toSphere(face, u, v);
                    float[] uv = CubeSphere.toFaceUV(dir);
                    CubeFace resolvedFace = CubeSphere.dominantFace(dir);
                    assertEquals(face, resolvedFace,
                        "Face mismatch for " + face + " u=" + u + " v=" + v);
                    assertEquals(u, uv[0], 0.02f,
                        "U mismatch for " + face + " u=" + u + " v=" + v);
                    assertEquals(v, uv[1], 0.02f,
                        "V mismatch for " + face + " u=" + u + " v=" + v);
                }
            }
        }
    }
}
