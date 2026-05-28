package com.galacticodyssey.data;

import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.List;

public final class AssetManifest {

    public record Entry(
        String id,
        String path,
        String lodMidPath,
        String lodFarSprite,
        int memoryTier,
        List<String> tags
    ) {}

    private final AssetCategory category;
    private final List<Entry> entries;

    private AssetManifest(AssetCategory category, List<Entry> entries) {
        this.category = category;
        this.entries = entries;
    }

    public static AssetManifest fromJson(JsonValue root) {
        AssetCategory category = AssetCategory.valueOf(root.getString("category"));
        List<Entry> entries = new ArrayList<>();
        for (JsonValue assetJson : root.get("assets")) {
            List<String> tags = new ArrayList<>();
            JsonValue tagsJson = assetJson.get("tags");
            if (tagsJson != null) {
                for (JsonValue tag : tagsJson) {
                    tags.add(tag.asString());
                }
            }
            entries.add(new Entry(
                assetJson.getString("id"),
                assetJson.getString("path"),
                assetJson.getString("lod_mid", null),
                assetJson.getString("lod_far", null),
                assetJson.getInt("memory_tier", 3),
                List.copyOf(tags)
            ));
        }
        return new AssetManifest(category, List.copyOf(entries));
    }

    public AssetCategory getCategory() { return category; }
    public List<Entry> getEntries() { return entries; }

    public Entry findById(String id) {
        return entries.stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .orElse(null);
    }
}
