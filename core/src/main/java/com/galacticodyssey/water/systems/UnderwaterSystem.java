package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.*;

public class UnderwaterSystem extends IteratingSystem {

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<DepthZoneComponent> zoneMapper =
        ComponentMapper.getFor(DepthZoneComponent.class);
    private final ComponentMapper<DiveGearComponent> gearMapper =
        ComponentMapper.getFor(DiveGearComponent.class);

    private final EventBus eventBus;
    private final SwimConfigData swimConfig;
    private final DepthZonesConfig zonesConfig;

    public UnderwaterSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            DepthZoneComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
        this.swimConfig = registry.getSwimConfig();
        this.zonesConfig = registry.getDepthZonesConfig();
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        DepthZoneComponent zone = zoneMapper.get(entity);

        if (swim.swimState == SwimState.DRY) return;

        float depth = swim.currentDepth;
        zone.currentDepth = depth;
        zone.ambientPressure = 1.0f + depth / 10.0f;

        DepthZone previousZone = zone.currentZone;
        zone.currentZone = resolveZone(depth);

        if (zone.currentZone != previousZone) {
            eventBus.publish(new DepthZoneChangedEvent(
                entity, previousZone, zone.currentZone, depth));
        }

        updateVisibility(zone, depth);
        checkPressureDamage(entity, zone, dt);
        checkAscentSickness(entity, swim);
        updateDiveGearOxygen(entity, swim, dt);
    }

    private DepthZone resolveZone(float depth) {
        if (zonesConfig == null || zonesConfig.zones == null) return DepthZone.SUNLIT;
        for (DepthZoneData zd : zonesConfig.zones) {
            if (depth >= zd.minDepth && depth < zd.maxDepth) {
                return DepthZone.valueOf(zd.id);
            }
        }
        return DepthZone.HADAL;
    }

    private void updateVisibility(DepthZoneComponent zone, float depth) {
        if (zonesConfig == null || zonesConfig.zones == null) return;
        for (DepthZoneData zd : zonesConfig.zones) {
            if (depth >= zd.minDepth && depth < zd.maxDepth) {
                float t = (depth - zd.minDepth) / Math.max(zd.maxDepth - zd.minDepth, 1f);
                zone.visibilityFraction = MathUtils.lerp(zd.visibilityStart, zd.visibilityEnd, t);
                zone.fogColor.set(zd.fogColorR, zd.fogColorG, zd.fogColorB, 1f);
                zone.requiresLight = zd.requiresLight;
                return;
            }
        }
    }

    private void checkPressureDamage(Entity entity, DepthZoneComponent zone, float dt) {
        DiveGearComponent gear = gearMapper.has(entity) ? gearMapper.get(entity) : null;
        float defaultMaxPressure = zonesConfig != null ? zonesConfig.noGearMaxPressure : 3.0f;
        float gearMaxPressure = gear != null ? gear.maxPressure : defaultMaxPressure;

        if (zone.ambientPressure > gearMaxPressure) {
            float excess = zone.ambientPressure - gearMaxPressure;
            DepthZoneData zoneData = findZoneData(zone.currentZone);
            float rate = zoneData != null ? zoneData.pressureDamageRate : 1.0f;
            float damage = rate * excess * dt;
            if (damage > 0f) {
                eventBus.publish(new PressureDamageEvent(
                    entity, damage, zone.ambientPressure, gearMaxPressure));
            }
        }
    }

    private void checkAscentSickness(Entity entity, SwimmingStateComponent swim) {
        if (swim.hasAscentSickness) return;
        if (swim.currentDepth < swimConfig.ascentSicknessMinDepth) return;
        if (swim.verticalSpeed < swimConfig.ascentSicknessSpeedThreshold) return;

        float speedRatio = swim.verticalSpeed / swimConfig.ascentSicknessSpeedThreshold;
        float duration = MathUtils.lerp(swimConfig.ascentSicknessMinDuration,
            swimConfig.ascentSicknessMaxDuration,
            MathUtils.clamp(speedRatio - 1f, 0f, 1f));

        swim.hasAscentSickness = true;
        swim.ascentSicknessTimer = duration;
        eventBus.publish(new AscentSicknessEvent(entity, swim.verticalSpeed, duration));
    }

    private void updateDiveGearOxygen(Entity entity, SwimmingStateComponent swim, float dt) {
        if (!gearMapper.has(entity)) return;
        DiveGearComponent gear = gearMapper.get(entity);

        if (swim.swimState == SwimState.DIVING || swim.swimState == SwimState.SUBMERGED) {
            gear.oxygenRemaining -= dt;
            if (gear.oxygenRemaining <= 0f) {
                gear.oxygenRemaining = 0f;
            }
        }
    }

    private DepthZoneData findZoneData(DepthZone zone) {
        if (zonesConfig == null || zonesConfig.zones == null) return null;
        String name = zone.name();
        for (DepthZoneData zd : zonesConfig.zones) {
            if (name.equals(zd.id)) return zd;
        }
        return null;
    }
}
