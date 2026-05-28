package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.fleet.data.FleetOrder;
import com.galacticodyssey.combat.fleet.data.FleetShipClass;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class FleetTacticsComponent implements Component {
    public final Map<String, Float> threatAssessment = new HashMap<>();
    public float engageMinRange = 500f;
    public float engageMaxRange = 2000f;
    public float retreatThreshold = 0.30f;
    public FleetShipClass priorityTargetClass;
    public final Queue<FleetOrder> orders = new ArrayDeque<>();
}
