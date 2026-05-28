package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BreachingPodComponent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.components.ShipEntryPointComponent;
import com.galacticodyssey.ship.docking.events.DockingCaptureEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Drives the boarding ATTACH phase. Two converging paths reach {@code BREACHED} + a ship-local
 * {@code entryLocalPosition}:
 * <ul>
 *   <li><b>Clamp</b> — reacts to {@link DockingCaptureEvent}; if one docked ship is VULNERABLE,
 *       bridges it to BREACHED via the target's airlock.</li>
 *   <li><b>Breach pod</b> — ticks launched {@link BreachingPodComponent} entities; on impact marks
 *       a hull breach and opens an entry point at the impact.</li>
 * </ul>
 */
public class BoardingAttachSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<ShipEntryPointComponent> ENTRY_M =
        ComponentMapper.getFor(ShipEntryPointComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BreachingPodComponent> POD_M =
        ComponentMapper.getFor(BreachingPodComponent.class);

    private final EventBus eventBus;
    private final Queue<DockingCaptureEvent> pendingDocks = new ArrayDeque<>();
    private ImmutableArray<Entity> pods;
    private final List<Entity> finishedPods = new ArrayList<>();
    private final Vector3 tmp = new Vector3();

    public BoardingAttachSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(DockingCaptureEvent.class, pendingDocks::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        pods = engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        pods = null;
    }

    @Override
    public void update(float deltaTime) {
        DockingCaptureEvent dock;
        while ((dock = pendingDocks.poll()) != null) {
            handleClamp(dock.portA, dock.portB);
        }
        tickPods(deltaTime);
    }

    private void handleClamp(Entity shipA, Entity shipB) {
        Entity target = vulnerable(shipA) ? shipA : (vulnerable(shipB) ? shipB : null);
        if (target == null) return;
        Entity aggressor = (target == shipA) ? shipB : shipA;

        BoardingOperationComponent op = OP_M.get(target);
        if (op.phase != BoardingPhase.VULNERABLE) return;

        ShipEntryPointComponent entry = ENTRY_M.get(target);
        Vector3 entryLocal = (entry != null) ? entry.interiorPosition : Vector3.Zero;

        breach(op, aggressor, target, AttachMethod.CLAMP, entryLocal);
    }

    private boolean vulnerable(Entity ship) {
        BoardingOperationComponent op = OP_M.get(ship);
        return op != null && op.phase == BoardingPhase.VULNERABLE;
    }

    /** Marks the operation BREACHED and announces it. Shared by clamp + pod paths. */
    private void breach(BoardingOperationComponent op, Entity aggressor, Entity target,
                        AttachMethod method, Vector3 entryLocal) {
        op.aggressorShip = aggressor;
        op.attachMethod = method;
        op.entryLocalPosition.set(entryLocal);
        op.phase = BoardingPhase.BREACHED;
        eventBus.publish(new ShipBreachedEvent(aggressor, target, method, op.entryLocalPosition));
    }

    private void tickPods(float deltaTime) {
        if (pods == null) return;
        finishedPods.clear();
        for (int i = 0, n = pods.size(); i < n; i++) {
            Entity podEntity = pods.get(i);
            BreachingPodComponent pod = POD_M.get(podEntity);
            if (pod.impacted) { finishedPods.add(podEntity); continue; }

            pod.elapsed += deltaTime;
            float t = pod.flightDuration <= 0f ? 1f : Math.min(1f, pod.elapsed / pod.flightDuration);
            // Advance pod transform (visual); impact at t>=1.
            TransformComponent podTransform = TRANSFORM_M.get(podEntity);
            if (podTransform != null) {
                podTransform.position.set(pod.origin).lerp(pod.impactPoint, t);
            }
            if (t >= 1f) {
                impactPod(pod);
                pod.impacted = true;
                finishedPods.add(podEntity);
            }
        }
        for (int i = 0; i < finishedPods.size(); i++) {
            getEngine().removeEntity(finishedPods.get(i));
        }
    }

    private void impactPod(BreachingPodComponent pod) {
        Entity target = pod.target;
        if (target == null) return;
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return;

        // Entry point in ship-local space = impactPoint relative to ship origin.
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            tmp.set(pod.impactPoint).sub(targetTransform.position);
        } else {
            tmp.set(pod.impactPoint);
        }
        breach(op, pod.aggressor, target, AttachMethod.BREACH_POD, tmp);
    }

    /**
     * Launches a breaching pod from {@code aggressor} toward {@code target}. The pod flies for
     * {@link BreachingPodComponent#flightDuration} seconds and then breaches the target.
     * Returns the created pod entity (already added to the engine), or {@code null} if the
     * target is not in a VULNERABLE boarding state.
     */
    public Entity launchPod(Entity aggressor, Entity target) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return null;

        TransformComponent aggressorT = TRANSFORM_M.get(aggressor);
        TransformComponent targetT = TRANSFORM_M.get(target);

        Entity podEntity = new Entity();
        BreachingPodComponent pod = new BreachingPodComponent();
        pod.aggressor = aggressor;
        pod.target = target;
        if (aggressorT != null) pod.origin.set(aggressorT.position);
        if (targetT != null) pod.impactPoint.set(targetT.position);
        podEntity.add(pod);

        TransformComponent podTransform = new TransformComponent();
        podTransform.position.set(pod.origin);
        podEntity.add(podTransform);

        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.ATTACHING;

        getEngine().addEntity(podEntity);
        return podEntity;
    }
}
