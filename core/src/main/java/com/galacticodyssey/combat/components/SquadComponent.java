package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.SquadRole;

public class SquadComponent implements Component {
    public int squadId;
    public SquadRole role = SquadRole.FLANKER;
    public final Vector3 formationOffset = new Vector3();
}
