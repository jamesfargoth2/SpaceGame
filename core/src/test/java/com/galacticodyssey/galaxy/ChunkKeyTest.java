package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkKeyTest {

    @Test
    void equalKeysAreEqual() {
        ChunkKey a = new ChunkKey(5, 10);
        ChunkKey b = new ChunkKey(5, 10);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentKeysAreNotEqual() {
        assertNotEquals(new ChunkKey(0, 0), new ChunkKey(1, 0));
        assertNotEquals(new ChunkKey(0, 0), new ChunkKey(0, 1));
    }

    @Test
    void worksAsHashMapKey() {
        Map<ChunkKey, String> map = new HashMap<>();
        map.put(new ChunkKey(1, 2), "hello");
        assertEquals("hello", map.get(new ChunkKey(1, 2)));
        assertNull(map.get(new ChunkKey(1, 3)));
    }
}
