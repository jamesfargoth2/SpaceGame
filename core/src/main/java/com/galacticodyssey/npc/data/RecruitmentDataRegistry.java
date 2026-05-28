package com.galacticodyssey.npc.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.npc.components.RecruitConditionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecruitmentDataRegistry {

    private final Map<String, RecruitConditionDefinition> conditions = new HashMap<>();
    private final Map<String, CantinaLayoutDefinition> layouts = new HashMap<>();

    public void registerCondition(RecruitConditionDefinition def) {
        conditions.put(def.id, def);
    }

    public RecruitConditionDefinition getCondition(String id) {
        return conditions.get(id);
    }

    public List<RecruitConditionDefinition> getAllConditions() {
        return new ArrayList<>(conditions.values());
    }

    public void registerLayout(String stationId, CantinaLayoutDefinition layout) {
        layouts.put(stationId, layout);
    }

    public CantinaLayoutDefinition getLayout(String stationId) {
        return layouts.get(stationId);
    }

    public void loadFromFiles() {
        JsonReader reader = new JsonReader();

        JsonValue condRoot = reader.parse(Gdx.files.internal("data/npcs/recruit_conditions.json"));
        for (JsonValue entry = condRoot.child; entry != null; entry = entry.next) {
            RecruitConditionDefinition def = new RecruitConditionDefinition();
            def.id = entry.getString("id");
            def.type = RecruitConditionType.valueOf(entry.getString("type"));
            def.targetId = entry.getString("targetId");
            def.description = entry.getString("description");
            def.weight = entry.getFloat("weight");
            registerCondition(def);
        }

        JsonValue layoutRoot = reader.parse(Gdx.files.internal("data/stations/cantina_layouts.json"));
        for (JsonValue entry = layoutRoot.child; entry != null; entry = entry.next) {
            CantinaLayoutDefinition layout = new CantinaLayoutDefinition();
            layout.backgroundKey = entry.getString("backgroundKey");
            layout.capacity = entry.getInt("capacity");
            JsonValue seatsArr = entry.get("seats");
            for (JsonValue s = seatsArr.child; s != null; s = s.next) {
                CantinaSeatDefinition seat = new CantinaSeatDefinition();
                seat.id = s.getString("id");
                seat.x = s.getFloat("x");
                seat.y = s.getFloat("y");
                layout.seats.add(seat);
            }
            JsonValue board = entry.get("hiringBoard");
            layout.hiringBoardX = board.getFloat("x");
            layout.hiringBoardY = board.getFloat("y");
            registerLayout(entry.name, layout);
        }
    }
}
