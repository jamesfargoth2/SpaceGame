package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import java.util.ArrayList;
import java.util.List;

public class ShipHardpointComponent implements Component {
    public final List<Hardpoint> hardpoints = new ArrayList<>();
    public Entity currentTarget;

    public Hardpoint getHardpoint(String id) {
        for (Hardpoint hp : hardpoints) {
            if (hp.id.equals(id)) return hp;
        }
        return null;
    }
}
