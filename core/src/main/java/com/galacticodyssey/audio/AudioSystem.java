package com.galacticodyssey.audio;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.ShieldAbsorbEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.events.ShipOverheatEvent;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;

/**
 * Subscribes to game EventBus and routes events to AudioManager for 3D positional playback.
 * GameScreen must call updateListener() each frame with the camera position and direction.
 */
public class AudioSystem {

    private static final ComponentMapper<TransformComponent> TRANSFORM =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> RANGED =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH =
        ComponentMapper.getFor(HealthComponent.class);

    private final AudioManager audioManager;
    private final SoundBindings bindings;

    private final Vector3 listenerPos = new Vector3();
    private final Vector3 listenerForward = new Vector3(0, 0, -1);

    public AudioSystem(EventBus eventBus, AudioManager audioManager) {
        this.audioManager = audioManager;
        this.bindings = new SoundBindings();
        bindings.load();
        subscribeAll(eventBus);
    }

    /** Called every frame by GameScreen to keep the listener in sync with the camera. */
    public void updateListener(Vector3 pos, Vector3 forward) {
        listenerPos.set(pos);
        listenerForward.set(forward).nor();
    }

    private void subscribeAll(EventBus eventBus) {
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
        eventBus.subscribe(ReloadStartedEvent.class, this::onReloadStarted);
        eventBus.subscribe(HitscanHitEvent.class, this::onHitscanHit);
        eventBus.subscribe(ProjectileHitEvent.class, this::onProjectileHit);
        eventBus.subscribe(ShieldAbsorbEvent.class, this::onShieldAbsorb);
        eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
        eventBus.subscribe(ShipWeaponFiredEvent.class, this::onShipWeaponFired);
        eventBus.subscribe(ShipOverheatEvent.class, this::onShipOverheat);
    }

    private void onWeaponFired(WeaponFiredEvent e) {
        TransformComponent t = TRANSFORM.get(e.shooter);
        if (t == null) return;
        RangedWeaponComponent ranged = RANGED.get(e.shooter);
        String key = ranged != null && ranged.damageType != null
            ? "WeaponFiredEvent:" + ranged.damageType.name()
            : "WeaponFiredEvent";
        play3D(key, t.position);
    }

    private void onReloadStarted(ReloadStartedEvent e) {
        TransformComponent t = TRANSFORM.get(e.entity);
        play3D("ReloadStartedEvent", t != null ? t.position : listenerPos);
    }

    private void onHitscanHit(HitscanHitEvent e) {
        // Classify hit surface: entities with HealthComponent are organic, rest are metal.
        String material = HEALTH.get(e.target) != null ? "flesh" : "metal";
        play3D("HitscanHitEvent:" + material, e.hitPoint);
    }

    private void onProjectileHit(ProjectileHitEvent e) {
        String key = e.damageType != null
            ? "ProjectileHitEvent:" + e.damageType.name()
            : "ProjectileHitEvent";
        play3D(key, e.hitPoint);
    }

    private void onShieldAbsorb(ShieldAbsorbEvent e) {
        TransformComponent t = TRANSFORM.get(e.target);
        play3D("ShieldAbsorbEvent", t != null ? t.position : listenerPos);
    }

    private void onEntityKilled(EntityKilledEvent e) {
        TransformComponent t = TRANSFORM.get(e.target);
        play3D("EntityKilledEvent", t != null ? t.position : listenerPos);
    }

    private void onShipWeaponFired(ShipWeaponFiredEvent e) {
        String key = e.weaponData != null && e.weaponData.category != null
            ? "ShipWeaponFiredEvent:" + e.weaponData.category.name()
            : "ShipWeaponFiredEvent";
        play3D(key, e.origin);
    }

    private void onShipOverheat(ShipOverheatEvent e) {
        TransformComponent t = TRANSFORM.get(e.shipEntity);
        play3D("ShipOverheatEvent", t != null ? t.position : listenerPos);
    }

    private void play3D(String key, Vector3 sourcePos) {
        String path = bindings.resolveWithFallback(key);
        if (path != null) {
            audioManager.play3D(path, sourcePos, listenerPos, listenerForward, 1f);
        }
    }
}
