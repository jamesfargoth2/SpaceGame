package com.galacticodyssey.mission.saga;

import java.util.HashMap;
import java.util.Map;

public class SagaRegistry {
    private final Map<String, SagaData> sagas = new HashMap<>();

    public void register(SagaData saga) { sagas.put(saga.id, saga); }
    public SagaData get(String id) { return sagas.get(id); }
    public Map<String, SagaData> getAll() { return sagas; }
}
