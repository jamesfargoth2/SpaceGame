package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;

public class GroundVehicleComponent implements Component {
    public float mass;
    public float wheelbase;
    public float trackWidth;
    public float groundClearance;
    public float maxDriveForce;
    public float maxSteerAngle;
    public float anchorBreakForce;
    public float dynamicLift;
    public AnchorConstraint anchor;
    public SurfaceMaterial currentSurface = SurfaceMaterial.BARE_ROCK;
    public float slipFraction;
    public float sinkageDepth;
    /** Driver throttle, -1 (full reverse) .. +1 (full forward). */
    public float throttleInput;
    /** Driver steering, -1 (left) .. +1 (right). */
    public float steerInput;
}
