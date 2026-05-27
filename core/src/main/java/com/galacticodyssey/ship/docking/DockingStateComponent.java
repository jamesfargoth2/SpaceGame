package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.DockingStateSnapshot;
import java.util.UUID;

/**
 * Tracks the current phase and parameters of a docking approach for a chaser entity.
 */
public class DockingStateComponent implements Component, Snapshotable<DockingStateSnapshot> {

    /** Phases of a docking sequence, ordered from initial approach to final structural lock. */
    public enum DockingPhase {
        NONE,
        FAR_APPROACH,
        MIDRANGE,
        FINAL_APPROACH,
        CONTACT,
        HARD_DOCK
    }

    /** Current phase of the docking sequence. */
    public DockingPhase dockingPhase = DockingPhase.NONE;

    /** The target entity this chaser is attempting to dock with. */
    public Entity targetEntity;

    /** Persisted UUID for {@link #targetEntity}; resolved by ReferenceResolver on load. */
    public UUID targetEntityId;

    /** Unit vector defining the centreline of the approach corridor in world space. */
    public final Vector3 approachAxis = new Vector3(0f, 0f, -1f);

    /** Half-angle of the approach cone measured from the approach axis (degrees). */
    public float coneHalfAngleDeg = 10f;

    /** Maximum allowed relative speed during the current approach phase (m/s). */
    public float maxApproachSpeed = 0.5f;

    @Override
    public DockingStateSnapshot takeSnapshot() {
        DockingStateSnapshot s = new DockingStateSnapshot();
        s.dockingPhase = dockingPhase.name();
        s.targetEntityId = targetEntityId;
        s.approachAxisX = approachAxis.x;
        s.approachAxisY = approachAxis.y;
        s.approachAxisZ = approachAxis.z;
        s.coneHalfAngleDeg = coneHalfAngleDeg;
        s.maxApproachSpeed = maxApproachSpeed;
        return s;
    }

    @Override
    public void restoreFromSnapshot(DockingStateSnapshot s) {
        dockingPhase = DockingPhase.valueOf(s.dockingPhase);
        targetEntityId = s.targetEntityId;
        // targetEntity is resolved later by ReferenceResolver
        approachAxis.set(s.approachAxisX, s.approachAxisY, s.approachAxisZ);
        coneHalfAngleDeg = s.coneHalfAngleDeg;
        maxApproachSpeed = s.maxApproachSpeed;
    }
}
