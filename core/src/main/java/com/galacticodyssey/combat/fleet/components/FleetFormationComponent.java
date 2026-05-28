package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;

public class FleetFormationComponent implements Component {
    public String formationTemplateId = "wedge";
    public double anchorX, anchorY, anchorZ;
    public float localAnchorX, localAnchorY, localAnchorZ;
    public float headingYaw, headingPitch;
    public float spacingScale = 1.0f;
}
