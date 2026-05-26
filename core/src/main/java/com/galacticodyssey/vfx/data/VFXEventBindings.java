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

    /**
     * Populates bindings from a pre-parsed map. Keys may be either:
     * <ul>
     *   <li>{@code "EventType:Variant"} — specific variant binding</li>
     *   <li>{@code "EventType"} — plain fallback binding</li>
     * </ul>
     */
    public void loadFromMap(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String effectId = entry.getValue();
            int colon = key.indexOf(':');
            if (colon >= 0) {
                String eventType = key.substring(0, colon);
                String variant = key.substring(colon + 1);
                bind(eventType, variant, effectId);
            } else {
                bind(key, null, effectId);
            }
        }
    }
}
