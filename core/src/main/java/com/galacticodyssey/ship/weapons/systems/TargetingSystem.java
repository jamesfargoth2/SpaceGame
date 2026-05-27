package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.player.components.PlayerTargetComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.events.TargetLockedEvent;
import com.galacticodyssey.ship.weapons.events.TargetLostEvent;

/**
 * Each frame, finds the closest targetable entity (ShipHardpointComponent + TransformComponent)
 * within a 5-degree cone around the camera forward direction and sets it as the player's soft target.
 * <p>
 * T key: hard-locks soft target, or unlocks if already locked on the same entity.
 * TAB / SHIFT+TAB: cycles through all targetable entities by distance.
 * Lead indicator: leadPos = targetPos + targetVel * (dist / avgProjectileSpeed).
 */
public class TargetingSystem extends EntitySystem {

    private static final float CONE_HALF_ANGLE_DEG = 5f;
    private static final float CONE_COS = MathUtils.cosDeg(CONE_HALF_ANGLE_DEG);
    private static final float AVG_PROJECTILE_SPEED = 500f;

    private final EventBus eventBus;

    /** Production path: reads position/direction from camera each frame. */
    private final PerspectiveCamera camera;

    /** Test path: mutable references supplied directly (camera is null). */
    private final Vector3 externalCamPos;
    private final Vector3 externalCamDir;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
            ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
            ComponentMapper.getFor(ShipFlightInputComponent.class);
    private final ComponentMapper<PlayerTargetComponent> targetMapper =
            ComponentMapper.getFor(PlayerTargetComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> targetableEntities;

    // Scratch vectors — never returned across frames.
    private final Vector3 toTarget = new Vector3();
    private final Vector3 camPos = new Vector3();
    private final Vector3 camDir = new Vector3();

    /** Production constructor — reads position/direction from the live PerspectiveCamera. */
    public TargetingSystem(EventBus eventBus, PerspectiveCamera camera) {
        super(6);
        this.eventBus = eventBus;
        this.camera = camera;
        this.externalCamPos = null;
        this.externalCamDir = null;
    }

    /**
     * Test-friendly constructor — supplies camera position and direction as plain Vector3 values
     * so that no libGDX native library (Matrix4 JNI) is required during unit tests.
     */
    public TargetingSystem(EventBus eventBus, Vector3 cameraPos, Vector3 cameraDir) {
        super(6);
        this.eventBus = eventBus;
        this.camera = null;
        this.externalCamPos = cameraPos;
        this.externalCamDir = cameraDir;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
                PlayerTagComponent.class, PlayerStateComponent.class, PlayerTargetComponent.class).get());
        targetableEntities = engine.getEntitiesFor(Family.all(
                ShipHardpointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING) return;

        PlayerTargetComponent target = targetMapper.get(player);
        ShipFlightInputComponent input = flightInputMapper.get(player);

        // Resolve camera position and direction from whichever source was provided.
        if (camera != null) {
            camPos.set(camera.position);
            camDir.set(camera.direction).nor();
        } else {
            camPos.set(externalCamPos);
            camDir.set(externalCamDir).nor();
        }

        // --- Soft target: closest targetable entity inside the cone ---
        Entity closestInCone = null;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < targetableEntities.size(); i++) {
            Entity candidate = targetableEntities.get(i);
            if (candidate == state.currentShip) continue; // ignore own ship

            TransformComponent ct = transformMapper.get(candidate);
            toTarget.set(ct.position).sub(camPos);
            float dist = toTarget.len();
            if (dist < 0.1f) continue;

            toTarget.nor();
            float dot = toTarget.dot(camDir);
            if (dot >= CONE_COS && dist < closestDist) {
                closestDist = dist;
                closestInCone = candidate;
            }
        }

        target.softTarget = closestInCone;

        // --- Hard lock toggle (T key) ---
        if (input != null && input.targetLockPressed) {
            input.targetLockPressed = false;
            if (target.lockedTarget != null && target.lockedTarget == target.softTarget) {
                // Same target pressed again — unlock.
                target.lockedTarget = null;
                eventBus.publish(new TargetLostEvent());
            } else if (target.softTarget != null) {
                target.lockedTarget = target.softTarget;
                eventBus.publish(new TargetLockedEvent(target.lockedTarget));
            }
        }

        // --- Cycle targets (TAB / Shift+TAB) ---
        if (input != null && input.nextTargetPressed) {
            input.nextTargetPressed = false;
            cycleTarget(target, state, 1);
        }
        if (input != null && input.prevTargetPressed) {
            input.prevTargetPressed = false;
            cycleTarget(target, state, -1);
        }

        // --- Lead indicator for locked target ---
        if (target.lockedTarget != null) {
            TransformComponent lockedTransform = transformMapper.get(target.lockedTarget);
            if (lockedTransform != null) {
                float dist = camPos.dst(lockedTransform.position);
                float timeToTarget = dist / AVG_PROJECTILE_SPEED;
                // leadPos = targetPos + targetVel * timeToTarget
                // Velocity would come from a PhysicsBodyComponent; default to target position (vel=0).
                target.leadIndicatorPos.set(lockedTransform.position);
                // Future: add velocity contribution when PhysicsBodyComponent is available.
            }
        }
    }

    private void cycleTarget(PlayerTargetComponent target, PlayerStateComponent state, int direction) {
        int n = targetableEntities.size();
        if (n == 0) return;

        // Find current locked index.
        int currentIndex = -1;
        if (target.lockedTarget != null) {
            for (int i = 0; i < n; i++) {
                if (targetableEntities.get(i) == target.lockedTarget) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // Advance by direction, skipping own ship.
        int nextIndex = (currentIndex + direction + n) % n;
        int attempts = 0;
        while (attempts < n) {
            Entity candidate = targetableEntities.get(nextIndex);
            if (candidate != state.currentShip) break;
            nextIndex = (nextIndex + direction + n) % n;
            attempts++;
        }

        Entity nextTarget = targetableEntities.get(nextIndex);
        if (nextTarget == state.currentShip) return; // all entities are own ship; do nothing

        if (target.lockedTarget != null && target.lockedTarget != nextTarget) {
            eventBus.publish(new TargetLostEvent());
        }
        target.lockedTarget = nextTarget;
        eventBus.publish(new TargetLockedEvent(nextTarget));
    }
}
