package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import java.util.HashMap;
import java.util.Map;

public class GrenadeInventoryComponent implements Component {
    public static final ComponentMapper<GrenadeInventoryComponent> MAPPER =
            ComponentMapper.getFor(GrenadeInventoryComponent.class);

    public final Map<String, Integer> grenades = new HashMap<>();
    public String selectedGrenadeType;
    public int maxPerType = 4;
    public float cookStartTime;
    public ThrowState throwState = ThrowState.IDLE;
}
