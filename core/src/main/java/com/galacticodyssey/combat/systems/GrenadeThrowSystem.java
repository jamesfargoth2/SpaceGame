package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.GrenadeInventoryComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.GrenadeThrowEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

import java.util.function.Supplier;

/**
 * Reads grenade throw input, manages the cook state machine, spawns grenade entities on release,
 * and handles overcook self-detonation.
 */
public class GrenadeThrowSystem extends IteratingSystem {

    public static final int PRIORITY = 3;
    private static final float ARC_BIAS_DEGREES = 15f;
    private static final float FORWARD_OFFSET = 1.0f;

    private final EventBus eventBus;
    private final GrenadeDataRegistry grenadeRegistry;
    private final Supplier<Float> worldTimeSupplier;

    public GrenadeThrowSystem(EventBus eventBus, GrenadeDataRegistry grenadeRegistry,
                              Supplier<Float> worldTimeSupplier) {
        super(Family.all(GrenadeInventoryComponent.class, CombatInputComponent.class,
                TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
        this.grenadeRegistry = grenadeRegistry;
        this.worldTimeSupplier = worldTimeSupplier;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        GrenadeInventoryComponent inv = GrenadeInventoryComponent.MAPPER.get(entity);
        CombatInputComponent input = entity.getComponent(CombatInputComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        if (inv.selectedGrenadeType == null) return;
        GrenadeData data = grenadeRegistry.get(inv.selectedGrenadeType);
        if (data == null) return;

        switch (inv.throwState) {
            case IDLE:
                if (input.grenadeThrowRequested && input.grenadeThrowHeld) {
                    int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
                    if (count > 0) {
                        inv.throwState = ThrowState.COOKING;
                        inv.cookStartTime = worldTimeSupplier.get();
                    }
                }
                break;

            case COOKING:
                float cookTime = worldTimeSupplier.get() - inv.cookStartTime;

                if (data.cookable && cookTime >= data.fuseDuration) {
                    detonateInHand(entity, inv, data, transform);
                    break;
                }

                if (!input.grenadeThrowHeld) {
                    throwGrenade(entity, inv, data, input, transform, cookTime);
                }
                break;

            case THROWN:
                inv.throwState = ThrowState.IDLE;
                break;
        }
    }

    private void detonateInHand(Entity entity, GrenadeInventoryComponent inv,
                                GrenadeData data, TransformComponent transform) {
        eventBus.publish(new DetonationEvent(
                entity, transform.position, data.damage,
                DamageType.EXPLOSIVE, data.blastRadius,
                data.blastFraction, data.thermalFraction,
                data.fragmentFraction, data.isDirectional));

        int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
        inv.grenades.put(inv.selectedGrenadeType, Math.max(0, count - 1));
        inv.throwState = ThrowState.IDLE;
    }

    private void throwGrenade(Entity player, GrenadeInventoryComponent inv,
                              GrenadeData data, CombatInputComponent input,
                              TransformComponent playerTransform, float cookTime) {
        Vector3 aimDir = new Vector3(input.aimDirection).nor();

        // Apply upward arc bias so the grenade arcs naturally over short cover
        float arcRad = ARC_BIAS_DEGREES * MathUtils.degreesToRadians;
        Vector3 right = new Vector3(aimDir).crs(Vector3.Y).nor();
        Vector3 up = new Vector3(right).crs(aimDir).nor();
        aimDir.mulAdd(up, MathUtils.sin(arcRad)).nor();

        float speed = data.throwForce / data.mass;
        Vector3 spawnPos = new Vector3(playerTransform.position)
                .mulAdd(input.aimDirection, FORWARD_OFFSET);

        Entity grenade = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        grenade.add(transform);

        ProjectileComponent proj = new ProjectileComponent();
        proj.velocity.set(aimDir).scl(speed);
        proj.speed = speed;
        proj.damage = data.damage;
        proj.damageType = DamageType.EXPLOSIVE;
        proj.areaOfEffect = data.blastRadius;
        proj.owner = player;
        proj.mass = data.mass;
        proj.dragCoeff = data.drag;
        proj.affectedByGravity = data.gravity;
        proj.lifetime = data.fuseDuration + 5f;
        grenade.add(proj);

        GrenadeComponent gc = new GrenadeComponent();
        gc.grenadeTypeId = data.id;
        gc.fuseType = data.fuseType;
        gc.fuseDuration = data.fuseDuration;
        gc.fuseTimer = data.cookable ? data.fuseDuration - cookTime : data.fuseDuration;
        gc.cookTime = data.cookable ? cookTime : 0f;
        gc.cookable = data.cookable;
        gc.bounceRestitution = data.bounceRestitution;
        gc.maxBounces = data.maxBounces;
        gc.damage = data.damage;
        gc.blastRadius = data.blastRadius;
        gc.blastFraction = data.blastFraction;
        gc.thermalFraction = data.thermalFraction;
        gc.fragmentFraction = data.fragmentFraction;
        gc.isDirectional = data.isDirectional;
        grenade.add(gc);

        getEngine().addEntity(grenade);

        int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
        inv.grenades.put(inv.selectedGrenadeType, Math.max(0, count - 1));
        inv.throwState = ThrowState.IDLE;

        eventBus.publish(new GrenadeThrowEvent(player, spawnPos, aimDir, data.id));
    }
}
