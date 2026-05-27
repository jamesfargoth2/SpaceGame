package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.events.BilgeAlarmEvent;
import com.galacticodyssey.water.events.DeckAwashEvent;
import com.galacticodyssey.water.events.DeckWashEvent;
import com.galacticodyssey.water.events.ShipSinkingEvent;

public class DeckWashSystem extends IteratingSystem {

    private final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<FloodingComponent> floodMapper =
        ComponentMapper.getFor(FloodingComponent.class);
    private final ComponentMapper<DeckWashComponent> washMapper =
        ComponentMapper.getFor(DeckWashComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private WaveSystem waveSystem;
    private float testWaterSurfaceHeight = Float.NaN;

    private static final float GRAVITY = 9.81f;

    public DeckWashSystem(int priority, EventBus eventBus) {
        super(Family.all(
            DeckWashComponent.class,
            HullComponent.class,
            FloodingComponent.class,
            PhysicsBodyComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
    }

    public void setWaveSystem(WaveSystem waveSystem) { this.waveSystem = waveSystem; }
    public void setTestWaterSurfaceHeight(float h) { this.testWaterSurfaceHeight = h; }

    @Override
    protected void processEntity(Entity entity, float dt) {
        HullComponent hull = hullMapper.get(entity);
        FloodingComponent flooding = floodMapper.get(entity);
        DeckWashComponent wash = washMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        Matrix4 worldTx = new Matrix4().idt();
        if (physics.body != null) {
            physics.body.getWorldTransform(worldTx);
        }

        float totalFlow = 0f;
        Vector3 worldPt = Pools.obtain(Vector3.class);

        for (int i = 0; i < wash.gunwaleSampleIndices.size; i++) {
            int idx = wash.gunwaleSampleIndices.get(i);
            if (idx >= hull.samplePoints.size) continue;

            BuoyancySamplePoint sp = hull.samplePoints.get(idx);
            worldPt.set(sp.localOffset).mul(worldTx);

            float waterHeight = getWaterHeight(worldPt);
            float overtoppingDepth = waterHeight - worldPt.y;

            if (overtoppingDepth > 0f) {
                float flow = wash.dischargeCd * wash.gunwaleSegmentLength
                    * (float) Math.sqrt(2f * GRAVITY * overtoppingDepth);
                totalFlow += flow * dt;
            }
        }

        Pools.free(worldPt);

        if (totalFlow > 0f) {
            Compartment target = findCompartment(flooding, wash.topCompartmentId);
            if (target != null) {
                target.waterVolume = Math.min(target.volume, target.waterVolume + totalFlow);
            }
            eventBus.publish(new DeckWashEvent(entity, totalFlow / dt));

            wash.deckAwashTimer += dt;
            if (!wash.deckAwash && wash.deckAwashTimer > DeckWashComponent.DECK_AWASH_THRESHOLD) {
                wash.deckAwash = true;
                eventBus.publish(new DeckAwashEvent(entity));
            }
        } else {
            wash.deckAwashTimer = Math.max(0f, wash.deckAwashTimer - dt);
            if (wash.deckAwashTimer <= 0f) {
                wash.deckAwash = false;
            }
        }

        checkFloodingAlarms(entity, flooding);
    }

    private void checkFloodingAlarms(Entity entity, FloodingComponent flooding) {
        float totalFloodedMass = 0f;
        boolean allSubmerged = true;
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            totalFloodedMass += comp.waterVolume * 1025f;
            if (comp.fillFraction() > 0.3f) {
                eventBus.publish(new BilgeAlarmEvent(entity, comp.id, comp.fillFraction()));
            }
            if (comp.fillFraction() < 1.0f) {
                allSubmerged = false;
            }
        }
        if (allSubmerged && flooding.compartments.size > 0) {
            eventBus.publish(new ShipSinkingEvent(entity, totalFloodedMass));
        }
    }

    private float getWaterHeight(Vector3 worldPos) {
        if (!Float.isNaN(testWaterSurfaceHeight)) {
            return testWaterSurfaceHeight;
        }
        if (waveSystem != null) {
            return waveSystem.getHeight(worldPos.x, worldPos.z);
        }
        return 0f;
    }

    private Compartment findCompartment(FloodingComponent flooding, String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment c = flooding.compartments.get(i);
            if (id.equals(c.id)) return c;
        }
        return null;
    }
}
