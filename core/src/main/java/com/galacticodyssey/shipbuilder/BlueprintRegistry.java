package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.*;

public class BlueprintRegistry {
    private final Map<String, BlueprintData> allBlueprints = new LinkedHashMap<>();
    private final Set<String> unlockedIds = new LinkedHashSet<>();

    public void loadFromFiles(String blueprintsPath, String startingPath) {
        Json json = new Json();
        JsonReader reader = new JsonReader();

        JsonValue bpArray = reader.parse(Gdx.files.internal(blueprintsPath));
        for (JsonValue entry = bpArray.child; entry != null; entry = entry.next) {
            BlueprintData bp = json.readValue(BlueprintData.class, entry);
            allBlueprints.put(bp.blueprintId, bp);
        }

        JsonValue startArray = reader.parse(Gdx.files.internal(startingPath));
        for (JsonValue id = startArray.child; id != null; id = id.next) {
            unlockedIds.add(id.asString());
        }
    }

    public void loadFromData(List<BlueprintData> blueprints, List<String> startingIds) {
        for (BlueprintData bp : blueprints) allBlueprints.put(bp.blueprintId, bp);
        unlockedIds.addAll(startingIds);
    }

    public BlueprintData get(String blueprintId) {
        return allBlueprints.get(blueprintId);
    }

    public boolean isUnlocked(String blueprintId) {
        return unlockedIds.contains(blueprintId);
    }

    public void unlock(String blueprintId) {
        unlockedIds.add(blueprintId);
    }

    public List<BlueprintData> getByType(BlueprintData.BlueprintType type) {
        List<BlueprintData> result = new ArrayList<>();
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == type) result.add(bp);
        }
        return result;
    }

    public List<BlueprintData> getUnlockedByType(BlueprintData.BlueprintType type) {
        List<BlueprintData> result = new ArrayList<>();
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == type && unlockedIds.contains(bp.blueprintId)) result.add(bp);
        }
        return result;
    }

    public boolean isRoomUnlocked(String roomTypeName) {
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == BlueprintData.BlueprintType.ROOM
                && bp.unlocks.equals(roomTypeName)
                && unlockedIds.contains(bp.blueprintId)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getUnlockedIds() {
        return Collections.unmodifiableSet(unlockedIds);
    }

    public void setUnlockedIds(Collection<String> ids) {
        unlockedIds.clear();
        unlockedIds.addAll(ids);
    }
}
