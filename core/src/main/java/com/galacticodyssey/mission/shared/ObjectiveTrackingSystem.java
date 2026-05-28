package com.galacticodyssey.mission.shared;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.CargoDeliveredEvent;
import com.galacticodyssey.core.events.EscortTargetReachedEvent;
import com.galacticodyssey.core.events.LocationEnteredEvent;
import com.galacticodyssey.core.events.NpcDialogueEvent;
import com.galacticodyssey.core.events.ResourceCollectedEvent;
import com.galacticodyssey.core.events.ScanCompleteEvent;
import com.galacticodyssey.mission.events.ObjectiveCompletedEvent;
import com.galacticodyssey.mission.events.ObjectiveUpdatedEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaState;

/**
 * Ashley EntitySystem that listens on the EventBus for world events and updates
 * active mission objectives in the QuestJournal.
 *
 * <p>The {@link #onEntityKilled(String)} method is public so that tests can call it
 * directly, and the EventBus subscription for {@link EntityKilledEvent} delegates to
 * it after extracting a targetId from the killed entity.
 */
public class ObjectiveTrackingSystem extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;

    public ObjectiveTrackingSystem(EventBus eventBus, QuestJournal journal) {
        this.eventBus = eventBus;
        this.journal = journal;
        subscribeAll();
    }

    // -------------------------------------------------------------------------
    // Subscriptions
    // -------------------------------------------------------------------------

    private void subscribeAll() {
        // EntityKilledEvent: extract a "targetId" string from the killed entity.
        // Ashley Entity has no built-in string id field; we use the entity's
        // identity hash code as a stable string key within a session.
        // Objectives that use entity-based kill targeting should store
        // Integer.toHexString(System.identityHashCode(entity)) as their targetId.
        // The public onEntityKilled() method is also callable directly from tests
        // or from code that already has the string id.
        eventBus.subscribe(EntityKilledEvent.class, e -> {
            if (e.target != null) {
                onEntityKilled(Integer.toHexString(System.identityHashCode(e.target)));
            }
        });

        eventBus.subscribe(LocationEnteredEvent.class,
                e -> increment(ObjectiveType.REACH_LOCATION, e.locationId));

        eventBus.subscribe(ScanCompleteEvent.class,
                e -> increment(ObjectiveType.SCAN_OBJECT, e.targetId));

        eventBus.subscribe(CargoDeliveredEvent.class,
                e -> increment(ObjectiveType.DELIVER_CARGO, e.cargoType));

        eventBus.subscribe(EscortTargetReachedEvent.class,
                e -> increment(ObjectiveType.ESCORT_TARGET, e.targetId));

        eventBus.subscribe(NpcDialogueEvent.class,
                e -> increment(ObjectiveType.TALK_TO_NPC, e.npcId));

        eventBus.subscribe(ResourceCollectedEvent.class, e -> {
            for (int i = 0; i < e.amount; i++) {
                increment(ObjectiveType.COLLECT_RESOURCE, e.resourceType);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called when an entity is killed.  The {@code targetId} string must match
     * the {@link Objective#targetId} of a DESTROY_TARGET objective.
     *
     * <p>This method is public so tests can drive it directly without needing to
     * publish an {@link EntityKilledEvent} (which requires an Ashley Entity).
     */
    public void onEntityKilled(String targetId) {
        increment(ObjectiveType.DESTROY_TARGET, targetId);
    }

    // -------------------------------------------------------------------------
    // ECS update loop — handles time-based objectives
    // -------------------------------------------------------------------------

    @Override
    public void update(float dt) {
        for (JobInstance job : journal.getActiveJobs()) {
            if (job.state != JobState.ACTIVE) continue;
            for (Objective obj : job.objectives) {
                if (!obj.completed && obj.type == ObjectiveType.SURVIVE_TIME) {
                    obj.currentCount = (int) (obj.currentCount + dt);
                    eventBus.publish(new ObjectiveUpdatedEvent(
                            job.instanceId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(job.instanceId, obj.id));
                        if (job.allRequiredComplete()) {
                            job.state = JobState.COMPLETE;
                            eventBus.publish(new QuestCompletedEvent(job.instanceId, job.reward));
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void increment(ObjectiveType type, String targetId) {
        for (JobInstance job : journal.getActiveJobs()) {
            if (job.state != JobState.ACTIVE) continue;
            for (Objective obj : job.objectives) {
                if (!obj.completed && obj.type == type && targetId.equals(obj.targetId)) {
                    obj.currentCount++;
                    eventBus.publish(new ObjectiveUpdatedEvent(
                            job.instanceId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(job.instanceId, obj.id));
                    }
                    if (job.allRequiredComplete()) {
                        job.state = JobState.COMPLETE;
                        eventBus.publish(new QuestCompletedEvent(job.instanceId, job.reward));
                    }
                }
            }
        }

        for (SagaInstance saga : journal.getAllActiveSagas()) {
            if (saga.state != SagaState.ACTIVE) continue;
            for (Objective obj : saga.activeObjectives) {
                if (!obj.completed && obj.type == type && targetId.equals(obj.targetId)) {
                    obj.currentCount++;
                    eventBus.publish(new ObjectiveUpdatedEvent(
                            saga.sagaDataId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(saga.sagaDataId, obj.id));
                    }
                }
            }
        }
    }
}
