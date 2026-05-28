package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.TrajectoryComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryPredictionSystemTest {

    private TrajectoryPredictionSystem system;

    @BeforeEach
    void setUp() {
        system = new TrajectoryPredictionSystem();
    }

    @Test
    void circularOrbitProducesCorrectElements() {
        float r = 100f;
        float GM = OrbitalConstants.G * 5.972e24f;
        float vCirc = OrbitalMechanics.circularOrbitSpeed(GM, r);

        Vector3 relPos = new Vector3(r, 0f, 0f);
        Vector3 relVel = new Vector3(0f, 0f, vCirc);

        TrajectoryComponent traj = new TrajectoryComponent();
        system.computeTrajectory(traj, relPos, relVel, 5.972e24f, new Vector3());

        assertNotNull(traj.currentOrbit);
        assertTrue(traj.isStable);
        assertEquals(r, traj.currentOrbit.semiMajorAxis, r * 0.01f);
        assertTrue(traj.currentOrbit.eccentricity < 0.05f);
        assertTrue(traj.predictedPath.size > 0);
    }

    @Test
    void escapeTrajectoryDetected() {
        float r = 100f;
        float GM = OrbitalConstants.G * 5.972e24f;
        float vEsc = OrbitalMechanics.escapeVelocity(GM, r) * 1.5f;

        Vector3 relPos = new Vector3(r, 0f, 0f);
        Vector3 relVel = new Vector3(0f, 0f, vEsc);

        TrajectoryComponent traj = new TrajectoryComponent();
        system.computeTrajectory(traj, relPos, relVel, 5.972e24f, new Vector3());

        assertNotNull(traj.currentOrbit);
        assertFalse(traj.isStable);
        assertTrue(traj.currentOrbit.eccentricity >= 1f);
    }
}
