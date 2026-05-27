package com.galacticodyssey.ship.atmosphere;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.atmosphere.AeroStateComponent.FlightRegime;
import com.galacticodyssey.ship.atmosphere.events.AtmosphereTransitionEvent;
import com.galacticodyssey.ship.atmosphere.events.StallEvent;

/**
 * Computes and applies aerodynamic lift and drag forces to entities that have
 * both an {@link AeroBodyComponent} and an {@link AeroStateComponent}.
 * <p>
 * Run at priority 5, before the ship flight system (priority 1 applies thrust;
 * this system overlays aero forces on top).
 * <p>
 * An {@link AtmosphereProfile} must be set via {@link #setAtmosphereProfile}
 * when the entity enters a planet's sphere of influence. When no profile is
 * set the system is a no-op.
 */
public class AeroForceSystem extends EntitySystem {

    private static final ComponentMapper<AeroBodyComponent> aeroBodyMapper =
        ComponentMapper.getFor(AeroBodyComponent.class);
    private static final ComponentMapper<AeroStateComponent> aeroStateMapper =
        ComponentMapper.getFor(AeroStateComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;

    private ImmutableArray<Entity> entities;
    private AtmosphereProfile atmosphere;

    // Scratch vectors to avoid per-frame allocation
    private final Vector3 velocity = new Vector3();
    private final Vector3 velNorm = new Vector3();
    private final Vector3 forward = new Vector3();
    private final Vector3 liftDir = new Vector3();
    private final Vector3 dragDir = new Vector3();
    private final Vector3 totalForce = new Vector3();
    private final Vector3 tmpCross = new Vector3();
    private final Matrix4 bodyTransform = new Matrix4();

    public AeroForceSystem(EventBus eventBus) {
        super(5);
        this.eventBus = eventBus;
    }

    /** Set the active atmosphere profile. Pass {@code null} to clear it. */
    public void setAtmosphereProfile(AtmosphereProfile profile) {
        this.atmosphere = profile;
    }

    /** Get the currently active atmosphere profile, or {@code null}. */
    public AtmosphereProfile getAtmosphereProfile() {
        return atmosphere;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(
            AeroBodyComponent.class,
            AeroStateComponent.class,
            PhysicsBodyComponent.class,
            TransformComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        if (atmosphere == null) return;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            processEntity(entity, deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        AeroBodyComponent aero = aeroBodyMapper.get(entity);
        AeroStateComponent state = aeroStateMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        if (physics.body == null) return;

        // Altitude approximation: distance from origin (planet centre is at
        // origin in local-space when near a planet).  Subtract planet radius
        // if available; for now treat position.len() as altitude.
        float altitude = transform.position.len();
        float density = atmosphere.densityAt(altitude);

        // --- atmosphere transition ---
        boolean wasInAtmosphere = aero.inAtmosphere;
        aero.inAtmosphere = density > 0f;
        if (aero.inAtmosphere != wasInAtmosphere) {
            eventBus.publish(new AtmosphereTransitionEvent(entity, aero.inAtmosphere));
        }

        if (density <= 0f) {
            clearState(state);
            return;
        }

        // Velocity from Bullet body
        velocity.set(physics.body.getLinearVelocity());
        float speed = velocity.len();
        if (speed < 0.1f) {
            clearState(state);
            return;
        }

        // Normalised velocity
        velNorm.set(velocity).scl(1f / speed);

        // Ship forward axis from physics body transform
        physics.body.getWorldTransform(bodyTransform);
        forward.set(0, 0, -1).rot(bodyTransform).nor();

        // Angle of attack: angle between velocity and forward
        float dot = MathUtils.clamp(forward.dot(velNorm), -1f, 1f);
        float aoa = (float) Math.acos(dot);
        // Give AoA a sign based on the pitch component
        tmpCross.set(forward).crs(velNorm);
        if (tmpCross.dot(1, 0, 0) < 0f) aoa = -aoa; // convention

        // Speed of sound and Mach
        float speedOfSound = atmosphere.speedOfSoundAt(altitude);
        float mach = (speedOfSound > 0f) ? speed / speedOfSound : 0f;

        // Dynamic pressure
        float q = 0.5f * density * speed * speed;

        // --- Lift coefficient ---
        float cl = liftCoefficient(aoa, aero.maxClAngle, aero.maxCl);
        boolean stalled = Math.abs(aoa) > aero.stallAngle;

        // --- Drag coefficient ---
        float cdInduced = (cl * cl) / (MathUtils.PI * aero.aspectRatio * aero.oswaldFactor);
        float cd = aero.cd0 + cdInduced;

        // --- Compressibility correction ---
        if (mach < 0.9f && mach > 0f) {
            // Prandtl-Glauert correction (subsonic only)
            float beta = (float) Math.sqrt(1f - mach * mach);
            cl /= beta;
            cd /= beta;
        }

        // --- Wave drag (supersonic) ---
        if (mach > 1.0f) {
            float machExcess = mach - 1.0f;
            // Simplified wave-drag rise
            float waveCd = 0.4f * machExcess * (float) Math.exp(-0.6f * machExcess);
            cd += waveCd;
        }

        // --- Force magnitudes ---
        float lift = q * aero.wingArea * cl;
        float drag = q * aero.wingArea * cd;

        // --- Lift direction: perpendicular to velocity in the lift plane ---
        // liftDir = normalize( (velNorm x forward) x velNorm )
        liftDir.set(velNorm).crs(forward).crs(velNorm).nor();
        if (liftDir.isZero(1e-6f)) {
            liftDir.set(0, 1, 0); // fallback when velocity is aligned with forward
        }

        // --- Drag direction: opposing velocity ---
        dragDir.set(velNorm).scl(-1f);

        // --- Apply forces via Bullet ---
        totalForce.set(liftDir).scl(lift).mulAdd(dragDir, drag);
        physics.body.applyCentralForce(totalForce);
        physics.body.activate();

        // --- Update state component ---
        state.mach = mach;
        state.angleOfAttack = aoa;
        state.currentLift = lift;
        state.currentDrag = drag;
        state.isStalled = stalled;
        state.flightRegime = FlightRegime.fromMach(mach);

        // --- Stall event ---
        if (stalled) {
            eventBus.publish(new StallEvent(entity, aoa));
        }
    }

    /**
     * Lift coefficient vs angle of attack: linear rise up to maxClAngle,
     * then a sharp drop-off past the stall angle.
     */
    private static float liftCoefficient(float aoa, float maxClAngle, float maxCl) {
        if (maxClAngle <= 0f) return 0f;
        float ratio = aoa / maxClAngle;
        if (Math.abs(ratio) <= 1f) {
            return maxCl * ratio; // pre-stall linear region
        }
        // Post-stall: Cl drops towards zero
        return maxCl * Math.signum(ratio) * Math.max(0f, 2f - Math.abs(ratio));
    }

    private static void clearState(AeroStateComponent state) {
        state.mach = 0f;
        state.angleOfAttack = 0f;
        state.currentLift = 0f;
        state.currentDrag = 0f;
        state.isStalled = false;
        state.flightRegime = FlightRegime.SUBSONIC;
    }
}
