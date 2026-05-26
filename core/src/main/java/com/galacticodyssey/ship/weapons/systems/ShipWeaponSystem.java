package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;

public class ShipWeaponSystem extends EntitySystem {
    private static final int PRIORITY = 4;
    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public ShipWeaponSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipHardpointComponent hpc = entity.getComponent(ShipHardpointComponent.class);
            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.fireTimer > 0) hp.fireTimer -= deltaTime;
            }
        }
    }

    public boolean fireHardpoint(Entity shipEntity, String hardpointId) {
        ShipHardpointComponent hpc = shipEntity.getComponent(ShipHardpointComponent.class);
        ShipWeaponHeatComponent heat = shipEntity.getComponent(ShipWeaponHeatComponent.class);
        TransformComponent tc = shipEntity.getComponent(TransformComponent.class);
        if (hpc == null || tc == null) return false;

        Hardpoint hp = hpc.getHardpoint(hardpointId);
        if (hp == null || hp.isEmpty()) return false;
        if (hp.currentState == HardpointState.DISABLED) return false;
        if (hp.fireTimer > 0) return false;
        if (heat != null && heat.isOverheated(hardpointId)) return false;
        if (!hp.mountedWeapon.canFire()) return false;

        hp.mountedWeapon.consumeAmmo();
        hp.fireTimer = 1f / hp.mountedWeapon.fireRate;

        if (heat != null) heat.addHeat(hardpointId, hp.mountedWeapon.heatPerShot);

        Vector3 origin = new Vector3(hp.position).add(tc.position);
        Vector3 direction = new Vector3(0, 0, 1);
        tc.rotation.transform(direction);

        eventBus.publish(new ShipWeaponFiredEvent(shipEntity, hardpointId, origin, direction, hp.mountedWeapon));
        return true;
    }
}
