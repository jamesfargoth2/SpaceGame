package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.terrain.SurfaceEvents.DustCloudSpawnedEvent;

public class DustSystem extends EntitySystem {

    private static final float PARTICLE_RADIUS  = 50e-6f;
    private static final float PARTICLE_DENSITY = 2500f;
    private static final float VISCOSITY        = 1.8e-5f;

    private final Pool<DustCloud> dustPool = new Pool<DustCloud>() {
        @Override protected DustCloud newObject() { return new DustCloud(); }
    };
    private final Array<DustCloud> activeClouds = new Array<>();
    private final EventBus eventBus;

    public DustSystem(EventBus eventBus) {
        super(6);
        this.eventBus = eventBus;
    }

    public DustCloud disturbDust(Vector3 origin, float disturbanceSpeed,
                                  SurfaceProperties surface, float gravity,
                                  float atmosphereDensity) {
        if (disturbanceSpeed < surface.dustSuspendThreshold) return null;
        DustCloud cloud = dustPool.obtain();
        cloud.origin.set(origin);
        cloud.radius = disturbanceSpeed * 0.5f;
        cloud.particleCount = (int) (disturbanceSpeed * 100f);
        cloud.settleRate = computeSettleRate(gravity, atmosphereDensity);
        cloud.lifetimeSeconds = atmosphereDensity < 0.001f
            ? 120f
            : 10f * (1f / (atmosphereDensity + 0.1f));
        activeClouds.add(cloud);
        eventBus.publish(new DustCloudSpawnedEvent(cloud));
        return cloud;
    }

    @Override
    public void update(float dt) {
        for (int i = activeClouds.size - 1; i >= 0; i--) {
            DustCloud c = activeClouds.get(i);
            c.elapsed += dt;
            c.radius += c.settleRate * dt * 10f;
            if (c.elapsed >= c.lifetimeSeconds) {
                activeClouds.removeIndex(i);
                dustPool.free(c);
            }
        }
    }

    private float computeSettleRate(float gravity, float atmosphereDensity) {
        if (atmosphereDensity < 0.001f) return 0.001f;
        return 2f * PARTICLE_RADIUS * PARTICLE_RADIUS
            * (PARTICLE_DENSITY - atmosphereDensity) * gravity / (9f * VISCOSITY);
    }

    public Array<DustCloud> getActiveClouds() {
        return activeClouds;
    }
}
