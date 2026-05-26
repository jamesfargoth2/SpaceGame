package com.galacticodyssey.galaxy.faction;

/** A patrol route defined by a sequence of waypoints in galaxy-space. */
public final class PatrolRoute {

    public final double[] waypointXs;
    public final double[] waypointYs;
    public final double[] waypointZs;
    public final boolean isAggressive;

    public PatrolRoute(double[] waypointXs, double[] waypointYs, double[] waypointZs,
                       boolean isAggressive) {
        this.waypointXs = waypointXs.clone();
        this.waypointYs = waypointYs.clone();
        this.waypointZs = waypointZs.clone();
        this.isAggressive = isAggressive;
    }
}
