package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class SurfaceEvents {

    private SurfaceEvents() {}

    public static final class WheelSlipEvent {
        public final Entity entity;
        public final float slipFraction;
        public WheelSlipEvent(Entity entity, float slipFraction) {
            this.entity = entity;
            this.slipFraction = slipFraction;
        }
    }

    public static final class LowGravLiftoffRiskEvent {
        public final Entity entity;
        public LowGravLiftoffRiskEvent(Entity entity) {
            this.entity = entity;
        }
    }

    public static final class VehicleSurfaceHeatEvent {
        public final Entity entity;
        public final float heatRateWatts;
        public VehicleSurfaceHeatEvent(Entity entity, float heatRateWatts) {
            this.entity = entity;
            this.heatRateWatts = heatRateWatts;
        }
    }

    public static final class DustCloudSpawnedEvent {
        public final DustCloud cloud;
        public DustCloudSpawnedEvent(DustCloud cloud) {
            this.cloud = cloud;
        }
    }

    public static final class SeismicStartedEvent {
        public final SeismicEvent quake;
        public SeismicStartedEvent(SeismicEvent quake) {
            this.quake = quake;
        }
    }

    public static final class SeismicEndedEvent {
        public final SeismicEvent quake;
        public SeismicEndedEvent(SeismicEvent quake) {
            this.quake = quake;
        }
    }

    public static final class AnchorDeployedEvent {
        public final Vector3 position;
        public AnchorDeployedEvent(Vector3 position) {
            this.position = position;
        }
    }
}
