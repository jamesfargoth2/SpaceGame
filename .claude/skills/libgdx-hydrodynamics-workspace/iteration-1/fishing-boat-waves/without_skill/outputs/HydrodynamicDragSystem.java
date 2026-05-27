package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WakeComponent;

/**
 * Applies hydrodynamic drag forces to surface vessels that have a {@link HullComponent}.
 * <p>
 * Drag is decomposed into three components:
 * <ol>
 *   <li><b>Viscous (skin friction):</b> {@code F = 0.5 * rho * Cf * A_wetted * v^2}
 *       &mdash; linear in coefficient, quadratic in speed.</li>
 *   <li><b>Form (pressure) drag:</b> {@code F = 0.5 * rho * Cd * A_frontal * v^2}
 *       &mdash; bluff-body resistance against broadside motion.</li>
 *   <li><b>Wave-making drag:</b> peaks at Froude 0.4&ndash;0.5 where a displacement hull
 *       climbs its own bow wave (hull-speed barrier).</li>
 * </ol>
 * <p>
 * Drag is computed against velocity <strong>relative to the water</strong>
 * (subtracting {@link WaterBodyComponent#currentVelocity}), not against the ground.
 * <p>
 * Angular damping resists rotation through the water, producing the heavy,
 * sluggish turning feel of a real boat. Quadratic angular drag is used so fast
 * spins are damped aggressively while gentle turns feel natural.
 * <p>
 * If the entity has a {@link WakeComponent}, the Froude number and wake intensity
 * are written for the rendering system.
 * <p>
 * Runs at priority 11 (after VesselBuoyancySystem at 10).
 */
public class HydrodynamicDragSystem extends EntitySystem {

    private static final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<WakeComponent> wakeMapper =
        ComponentMapper.getFor(WakeComponent.class);

    private ImmutableArray<Entity> vessels;

    /** Active water body; set externally when the player enters a water planet. */
    private WaterBodyComponent waterBody;

    // Scratch objects reused across processEntity calls within the same update tick
    private final Matrix4 tempTransform = new Matrix4();
    private final Matrix4 tempInverse = new Matrix4();

    public HydrodynamicDragSystem() {
        super(11);
    }

    /** Sets the active water body. Pass {@code null} when leaving water. */
    public void setWaterBody(WaterBodyComponent waterBody) {
        this.waterBody = waterBody;
    }

    @Override
    public void addedToEngine(Engine engine) {
        vessels = engine.getEntitiesFor(Family.all(
            HullComponent.class,
            PhysicsBodyComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        if (waterBody == null) return;

        for (int i = 0; i < vessels.size(); i++) {
            processEntity(vessels.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null) return;

        // Only apply drag if at least one sample point is submerged
        boolean anySubmerged = false;
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (hull.samplePoints.get(i).submerged) {
                anySubmerged = true;
                break;
            }
        }
        if (!anySubmerged) return;

        Vector3 linearVel = Pools.obtain(Vector3.class);
        Vector3 relVel = Pools.obtain(Vector3.class);
        Vector3 localRelVel = Pools.obtain(Vector3.class);
        Vector3 dragLocal = Pools.obtain(Vector3.class);
        Vector3 dragWorld = Pools.obtain(Vector3.class);
        Vector3 angVel = Pools.obtain(Vector3.class);
        Vector3 angDamp = Pools.obtain(Vector3.class);

        try {
            physics.body.getWorldTransform(tempTransform);
            physics.body.getLinearVelocity(linearVel);

            // Velocity relative to the water, not the ground
            relVel.set(linearVel).sub(waterBody.currentVelocity);
            float speed = relVel.len();
            if (speed < 0.001f) return;

            // Transform relative velocity to body-local frame for axis-decomposed drag
            tempInverse.set(tempTransform);
            tempInverse.inv();
            localRelVel.set(relVel).rot(tempInverse);

            final float rho = waterBody.density;

            // --- Forward axis (local Z): streamlined skin friction ---
            float vFwd = localRelVel.z;
            float dragFwd = -0.5f * rho * vFwd * Math.abs(vFwd)
                           * hull.dragCoefficientLinear * hull.wettedArea;

            // --- Lateral axis (local X): broadside form drag ---
            // High drag coefficient makes the boat resist sideways sliding,
            // which is what gives boats their characteristic heavy turning feel.
            float vLat = localRelVel.x;
            float lateralArea = hull.hullLength * getSubmergedDraft(hull);
            float dragLat = -0.5f * rho * vLat * Math.abs(vLat)
                           * hull.dragCoefficientQuad * lateralArea;

            // --- Vertical axis (local Y): resist bobbing ---
            float vVert = localRelVel.y;
            float verticalArea = hull.hullLength * hull.beamWidth * 0.5f;
            float dragVert = -0.5f * rho * vVert * Math.abs(vVert)
                            * hull.dragCoefficientQuad * verticalArea;

            // --- Wave-making drag (surface vessels only) ---
            float waveDrag = 0f;
            if (!isFullySubmerged(hull)) {
                float froude = speed / (float) Math.sqrt(9.81f * hull.hullLength);
                waveDrag = computeWaveDrag(froude, rho, hull);

                // Update wake component for rendering
                if (wakeMapper.has(entity)) {
                    WakeComponent wake = wakeMapper.get(entity);
                    wake.froudeNumber = froude;
                    wake.wakeIntensity = MathUtils.clamp(froude / 0.5f, 0f, 1f);
                }
            }

            // Compose drag force in local frame, then transform to world
            dragLocal.set(dragLat, dragVert, dragFwd);
            dragWorld.set(dragLocal).rot(tempTransform);

            // Add wave-making drag along the velocity direction
            if (waveDrag > 0f) {
                // Wave drag opposes velocity direction
                Vector3 waveForce = Pools.obtain(Vector3.class);
                waveForce.set(relVel).nor().scl(-waveDrag);
                dragWorld.add(waveForce);
                Pools.free(waveForce);
            }

            physics.body.applyCentralForce(dragWorld);

            // --- Angular damping: water resists rotation ---
            // Quadratic in angular speed for a heavy turning feel
            physics.body.getAngularVelocity(angVel);
            float angSpeed = angVel.len();
            if (angSpeed > 0.001f) {
                float angDragCoeff = 0.5f * rho * hull.dragCoefficientQuad
                                   * hull.hullLength * hull.beamWidth * 0.25f;
                float angDragMag = angDragCoeff * angSpeed * angSpeed;
                // Cap to prevent numerical explosion on tiny timesteps
                angDragMag = Math.min(angDragMag, 100000f);
                angDamp.set(angVel).nor().scl(-angDragMag);
                physics.body.applyTorque(angDamp);
            }

            physics.body.activate();
        } finally {
            Pools.free(linearVel);
            Pools.free(relVel);
            Pools.free(localRelVel);
            Pools.free(dragLocal);
            Pools.free(dragWorld);
            Pools.free(angVel);
            Pools.free(angDamp);
        }
    }

    /**
     * Estimates submerged draft from the average depth of submerged sample points.
     */
    private float getSubmergedDraft(HullComponent hull) {
        float totalDepth = 0f;
        int count = 0;
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (hull.samplePoints.get(i).submerged) {
                totalDepth += hull.samplePoints.get(i).depth;
                count++;
            }
        }
        return count > 0 ? totalDepth / count : 0f;
    }

    /** Returns true if every sample point is submerged. */
    private boolean isFullySubmerged(HullComponent hull) {
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (!hull.samplePoints.get(i).submerged) return false;
        }
        return true;
    }

    /**
     * Wave-making drag peaks near Froude 0.4-0.5 where a displacement hull climbs
     * its own bow wave (hull-speed barrier). Modelled as a Gaussian hump.
     */
    private float computeWaveDrag(float froude, float rho, HullComponent hull) {
        final float peak = 0.45f;
        final float width = 0.15f;
        float diff = froude - peak;
        float envelope = (float) Math.exp(-(diff * diff) / (2f * width * width));
        float cw = 0.005f * envelope;
        return 0.5f * rho * cw * hull.wettedArea * froude * froude
               * 9.81f * hull.hullLength;
    }
}
