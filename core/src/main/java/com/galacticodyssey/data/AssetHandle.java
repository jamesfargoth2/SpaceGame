package com.galacticodyssey.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AssetHandle<T> {

    private final String assetId;
    private final AssetCategory category;
    private volatile T asset;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private final Consumer<AssetHandle<T>> onZero;

    public AssetHandle(String assetId, AssetCategory category, Consumer<AssetHandle<T>> onZero) {
        this.assetId = assetId;
        this.category = category;
        this.onZero = onZero;
    }

    public AssetHandle<T> retain() {
        refCount.incrementAndGet();
        return this;
    }

    public void release() {
        if (refCount.decrementAndGet() <= 0 && onZero != null) {
            onZero.accept(this);
        }
    }

    public void setAsset(T asset) {
        this.asset = asset;
    }

    public T get() { return asset; }
    public boolean isResident() { return asset != null; }
    public String getAssetId() { return assetId; }
    public AssetCategory getCategory() { return category; }
    public int getRefCount() { return refCount.get(); }
}
