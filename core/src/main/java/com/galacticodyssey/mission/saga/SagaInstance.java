package com.galacticodyssey.mission.saga;

import com.galacticodyssey.mission.shared.Objective;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SagaInstance {
    public String sagaDataId;
    public String currentNodeId;
    public SagaState state = SagaState.LOCKED;
    public Map<String, String> choicesMade = new HashMap<>();
    public List<Objective> activeObjectives = new ArrayList<>();

    public boolean allRequiredObjectivesComplete() {
        return activeObjectives.stream().filter(o -> !o.optional).allMatch(o -> o.completed);
    }
}
