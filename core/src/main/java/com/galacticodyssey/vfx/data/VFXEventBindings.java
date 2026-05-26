package com.galacticodyssey.vfx.data;

import java.util.HashMap;
import java.util.Map;

public class VFXEventBindings {
    private final Map<String, String> bindings = new HashMap<>();

    public void bind(String eventType, String variant, String effectId) {
        String key = variant != null ? eventType + ":" + variant : eventType;
        bindings.put(key, effectId);
    }

    public String resolve(String eventType, String variant) {
        if (variant != null) {
            String specific = bindings.get(eventType + ":" + variant);
            if (specific != null) return specific;
        }
        return bindings.get(eventType);
    }
}
