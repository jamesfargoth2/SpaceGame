package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;

public class GuidedProjectileComponent implements Component, Pool.Poolable {

    public enum GuidancePhase {
        BOOST,
        COAST,
        TERMINAL,
        DETONATION
    }

    public Entity targetEntity;
    public float turnRate = 90f;
    public float armingDistance = 20f;
    public float flareVulnerability = 0.3f;
    public float distanceTraveled;

    public GuidancePhase phase = GuidancePhase.BOOST;
    public float navigationGain = 3f;
    public float maxAcceleration = 50f;
    public float boostDuration = 2f;
    public float boostTimer;
    public float terminalRange = 500f;
    public float fuelRemaining = 1f;

    @Override
    public void reset() {
        targetEntity = null;
        turnRate = 90f;
        armingDistance = 20f;
        flareVulnerability = 0.3f;
        distanceTraveled = 0f;
        phase = GuidancePhase.BOOST;
        navigationGain = 3f;
        maxAcceleration = 50f;
        boostDuration = 2f;
        boostTimer = 0f;
        terminalRange = 500f;
        fuelRemaining = 1f;
    }
}
