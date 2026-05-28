package com.galacticodyssey.networking.interpolation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntitySnapshotTest {

    @Test
    void storesFields() {
        EntitySnapshot s = new EntitySnapshot(5, 1, 2, 3, 0, 0, 0, 1, 4, 5, 6);
        assertEquals(5, s.tick);
        assertEquals(1f, s.posX);
        assertEquals(2f, s.posY);
        assertEquals(3f, s.posZ);
        assertEquals(1f, s.rotW);
    }

    @Test
    void lerpAtZeroReturnsFirst() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 0f);
        assertEquals(0f, r.posX, 1e-5f);
        assertEquals(0f, r.posY, 1e-5f);
    }

    @Test
    void lerpAtOneReturnsSecond() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 1f);
        assertEquals(10f, r.posX, 1e-5f);
        assertEquals(20f, r.posY, 1e-5f);
    }

    @Test
    void lerpAtHalfInterpolates() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 0.5f);
        assertEquals(5f, r.posX, 1e-5f);
        assertEquals(10f, r.posY, 1e-5f);
        assertEquals(15f, r.posZ, 1e-5f);
    }

    @Test
    void extrapolateMovesAlongVelocity() {
        EntitySnapshot s = new EntitySnapshot(10, 0, 0, 0, 0, 0, 0, 1, 5, 0, 0);
        EntitySnapshot e = s.extrapolate(0.1f, 0.05f);
        assertEquals(0.5f, e.posX, 1e-5f);
    }
}
