package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import java.util.ArrayList;
import java.util.List;

public class WeaponGroupComponent implements Component {
    @SuppressWarnings("unchecked")
    public final List<String>[] groups = new List[4];
    public int activeGroup;

    public WeaponGroupComponent() {
        for (int i = 0; i < 4; i++) {
            groups[i] = new ArrayList<>();
        }
    }
}
