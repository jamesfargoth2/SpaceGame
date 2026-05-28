package com.galacticodyssey.data;

import java.util.Comparator;
import java.util.PriorityQueue;

public class StreamingQueue {

    public static final class StreamRequest {
        public final String assetId;
        public final AssetCategory category;
        public final float priority;

        StreamRequest(String assetId, AssetCategory category, float distanceToCamera) {
            this.assetId = assetId;
            this.category = category;
            float inverseDist = distanceToCamera > 0f ? (1.0f / distanceToCamera) : Float.MAX_VALUE;
            this.priority = inverseDist * category.priorityWeight;
        }
    }

    // Max-heap: highest priority dequeued first
    private final PriorityQueue<StreamRequest> queue =
        new PriorityQueue<>(Comparator.comparingDouble((StreamRequest r) -> r.priority).reversed());

    public void enqueue(String assetId, AssetCategory category, float distanceToCamera) {
        queue.offer(new StreamRequest(assetId, category, distanceToCamera));
    }

    public StreamRequest poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}
