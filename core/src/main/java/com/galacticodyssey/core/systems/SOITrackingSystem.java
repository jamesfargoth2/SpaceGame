package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.SOITrackerComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.SOIChangedEvent;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalSlot;

public class SOITrackingSystem extends EntitySystem {

    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SOITrackerComponent> soiMapper =
        ComponentMapper.getFor(SOITrackerComponent.class);
    private static final ComponentMapper<OrbitalBodyComponent> orbitalMapper =
        ComponentMapper.getFor(OrbitalBodyComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<GravitySourceComponent> gravitySourceMapper =
        ComponentMapper.getFor(GravitySourceComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> trackedEntities;
    private ImmutableArray<Entity> celestialBodies;

    public SOITrackingSystem(EventBus eventBus) {
        super(4);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        trackedEntities = engine.getEntitiesFor(
            Family.all(SOITrackerComponent.class, TransformComponent.class).get());
        celestialBodies = engine.getEntitiesFor(
            Family.all(OrbitalBodyComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        trackedEntities = null;
        celestialBodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (trackedEntities == null || celestialBodies == null) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < trackedEntities.size(); i++) {
                Entity entity = trackedEntities.get(i);
                updateSOI(entity, tmp);
            }
        } finally {
            Pools.free(tmp);
        }
    }

    private void updateSOI(Entity entity, Vector3 tmp) {
        TransformComponent entityTransform = transformMapper.get(entity);
        SOITrackerComponent tracker = soiMapper.get(entity);

        Entity star = null;
        Entity bestPlanet = null;
        float bestPlanetDist = Float.MAX_VALUE;
        Entity bestMoon = null;
        float bestMoonDist = Float.MAX_VALUE;

        for (int j = 0; j < celestialBodies.size(); j++) {
            Entity body = celestialBodies.get(j);
            OrbitalBodyComponent orbital = orbitalMapper.get(body);
            TransformComponent bodyTransform = transformMapper.get(body);

            float dist = tmp.set(entityTransform.position).sub(bodyTransform.position).len();

            if (orbital.bodyType == CelestialBodyType.STAR) {
                star = body;
            } else if (orbital.bodyType == CelestialBodyType.PLANET) {
                if (dist < orbital.soiRadius && dist < bestPlanetDist) {
                    bestPlanet = body;
                    bestPlanetDist = dist;
                }
            } else if (orbital.bodyType == CelestialBodyType.MOON) {
                if (dist < orbital.soiRadius && dist < bestMoonDist) {
                    bestMoon = body;
                    bestMoonDist = dist;
                }
            }
        }

        Entity newDominant;
        Entity newSecondary;
        float distToDominant;

        if (bestMoon != null && bestPlanet != null) {
            newDominant = bestMoon;
            newSecondary = bestPlanet;
            distToDominant = bestMoonDist;
        } else if (bestPlanet != null) {
            newDominant = bestPlanet;
            newSecondary = star;
            distToDominant = bestPlanetDist;
        } else {
            newDominant = star;
            newSecondary = null;
            distToDominant = star != null
                ? tmp.set(entityTransform.position).sub(transformMapper.get(star).position).len()
                : 0f;
        }

        if (newDominant != tracker.dominantBody && tracker.dominantBody != null) {
            convertVelocityFrame(entity, tracker.dominantBody, newDominant, tmp);
            eventBus.publish(new SOIChangedEvent(entity, tracker.dominantBody, newDominant));
        }

        tracker.dominantBody = newDominant;
        tracker.secondaryBody = newSecondary;
        tracker.distanceToDominant = distToDominant;
    }

    private void convertVelocityFrame(Entity entity, Entity oldBody, Entity newBody, Vector3 tmp) {
        if (!physicsMapper.has(entity)) return;
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null) return;

        Vector3 oldVel = Pools.obtain(Vector3.class);
        Vector3 newVel = Pools.obtain(Vector3.class);
        try {
            computeBodyOrbitalVelocity(oldBody, oldVel);
            computeBodyOrbitalVelocity(newBody, newVel);

            Vector3 shipVel = physics.body.getLinearVelocity();
            shipVel.sub(oldVel).add(newVel);
            physics.body.setLinearVelocity(shipVel);
        } finally {
            Pools.free(oldVel);
            Pools.free(newVel);
        }
    }

    private void computeBodyOrbitalVelocity(Entity body, Vector3 out) {
        out.setZero();
        OrbitalBodyComponent orbital = orbitalMapper.get(body);
        if (orbital == null || orbital.orbitalSlot == null) return;
        if (orbital.bodyType == CelestialBodyType.STAR) return;

        OrbitalSlot slot = orbital.orbitalSlot;
        float GM;
        if (orbital.parentBody != null && gravitySourceMapper.has(orbital.parentBody)) {
            GM = OrbitalConstants.G * gravitySourceMapper.get(orbital.parentBody).mass;
        } else {
            return;
        }

        float a = slot.orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS;
        float e = slot.eccentricity;
        float nu = slot.currentTrueAnomaly;
        float p = a * (1f - e * e);
        if (p <= 0f) return;

        float sqrtGMoverP = (float) Math.sqrt(GM / p);
        float vx = sqrtGMoverP * (-MathUtils.sin(nu));
        float vz = sqrtGMoverP * (e + MathUtils.cos(nu));
        out.set(vx, 0f, vz);
    }
}
