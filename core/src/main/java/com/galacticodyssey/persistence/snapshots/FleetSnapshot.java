package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class FleetSnapshot {
    public String fleetId;
    public String factionId;
    public String fleetName;
    public String doctrine;
    public String state;
    public float aggregateFirepower;
    public float aggregateHP;
    public float aggregateSpeed;
    public boolean expanded;
    public List<FleetShipEntrySnapshot> composition = new ArrayList<>();

    public static class FleetShipEntrySnapshot {
        public String shipClass;
        public int count;
        public float avgHpRatio;
    }
}
