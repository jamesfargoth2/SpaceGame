package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class GuidedProjectileComponent implements Component {
    public Entity targetEntity;
    public float turnRate = 90f;
    public float armingDistance = 20f;
    public float flareVulnerability = 0.3f;
    public float distanceTraveled;
}
