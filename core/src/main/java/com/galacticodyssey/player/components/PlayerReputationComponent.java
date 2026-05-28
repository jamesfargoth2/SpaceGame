package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

import java.util.HashMap;
import java.util.Map;

public class PlayerReputationComponent implements Component {

    public static final ComponentMapper<PlayerReputationComponent> MAPPER =
        ComponentMapper.getFor(PlayerReputationComponent.class);

    public final Map<String, Float> standings = new HashMap<>();
}
