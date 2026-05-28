package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamingQueueTest {

    @Test
    void emptyQueueReturnsTrueForIsEmpty() {
        StreamingQueue q = new StreamingQueue();
        assertTrue(q.isEmpty());
    }

    @Test
    void enqueueAndPollSingleItem() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("char1", AssetCategory.CHARACTER, 10f);
        assertFalse(q.isEmpty());
        StreamingQueue.StreamRequest r = q.poll();
        assertNotNull(r);
        assertEquals("char1", r.assetId);
        assertEquals(AssetCategory.CHARACTER, r.category);
        assertTrue(q.isEmpty());
    }

    @Test
    void closerItemPolledBeforeFartherSameCategory() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("far", AssetCategory.CHARACTER, 100f);
        q.enqueue("close", AssetCategory.CHARACTER, 5f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("close", first.assetId);
    }

    @Test
    void highPriorityCategoryBeatsDistanceAdvantage() {
        // CHARACTER (weight 10) at 20m vs FOLIAGE (weight 4) at 20m
        // CHARACTER should come first
        StreamingQueue q = new StreamingQueue();
        q.enqueue("foliage1", AssetCategory.FOLIAGE, 20f);
        q.enqueue("char1", AssetCategory.CHARACTER, 20f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("char1", first.assetId);
    }

    @Test
    void zeroDistanceGetsMaxPriority() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("far", AssetCategory.BUILDING, 500f);
        q.enqueue("here", AssetCategory.BUILDING, 0f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("here", first.assetId);
    }

    @Test
    void clearEmptiesQueue() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("a", AssetCategory.PROP_SMALL, 10f);
        q.enqueue("b", AssetCategory.PROP_SMALL, 20f);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void sizeReflectsEnqueueCount() {
        StreamingQueue q = new StreamingQueue();
        assertEquals(0, q.size());
        q.enqueue("a", AssetCategory.FOLIAGE, 10f);
        assertEquals(1, q.size());
        q.enqueue("b", AssetCategory.FOLIAGE, 20f);
        assertEquals(2, q.size());
        q.poll();
        assertEquals(1, q.size());
    }
}
