package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AssetHandleTest {

    @Test
    void newHandleIsNotResident() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        assertFalse(h.isResident());
        assertNull(h.get());
    }

    @Test
    void setAssetMakesHandleResident() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        h.setAsset("loaded");
        assertTrue(h.isResident());
        assertEquals("loaded", h.get());
    }

    @Test
    void retainIncreasesRefCount() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        assertEquals(0, h.getRefCount());
        h.retain();
        assertEquals(1, h.getRefCount());
        h.retain();
        assertEquals(2, h.getRefCount());
    }

    @Test
    void releaseDecrementsRefCount() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        h.retain();
        h.retain();
        h.release();
        assertEquals(1, h.getRefCount());
    }

    @Test
    void releaseToZeroTriggersCallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER,
            handle -> callCount.incrementAndGet());
        h.retain();
        h.release();
        assertEquals(1, callCount.get());
    }

    @Test
    void callbackNotFiredAboveZero() {
        AtomicInteger callCount = new AtomicInteger(0);
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER,
            handle -> callCount.incrementAndGet());
        h.retain();
        h.retain();
        h.release(); // still at 1
        assertEquals(0, callCount.get());
    }

    @Test
    void getAssetIdAndCategory() {
        AssetHandle<String> h = new AssetHandle<>("myAsset", AssetCategory.FOLIAGE, null);
        assertEquals("myAsset", h.getAssetId());
        assertEquals(AssetCategory.FOLIAGE, h.getCategory());
    }
}
