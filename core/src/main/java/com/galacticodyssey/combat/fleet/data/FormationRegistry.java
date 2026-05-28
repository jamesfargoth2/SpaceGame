package com.galacticodyssey.combat.fleet.data;

import java.util.HashMap;
import java.util.Map;

public final class FormationRegistry {
    private final Map<String, FormationTemplate> templates = new HashMap<>();

    public void registerDefaults(int maxSlots) {
        register(FormationTemplate.line(maxSlots));
        register(FormationTemplate.wedge(maxSlots));
        register(FormationTemplate.box(maxSlots));
        register(FormationTemplate.sphere(maxSlots));
        register(FormationTemplate.wall(maxSlots));
        register(FormationTemplate.scattered(maxSlots, 42L));
    }

    public void register(FormationTemplate template) {
        templates.put(template.id, template);
    }

    public FormationTemplate get(String id) {
        return templates.get(id);
    }
}
