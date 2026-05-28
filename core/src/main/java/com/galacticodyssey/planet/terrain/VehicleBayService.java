package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleRegistry;
import com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent;
import com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;

/** Deploy/retrieve coordinator for a ship's vehicle bay. */
public class VehicleBayService {

    private final Engine engine;
    private final btDiscreteDynamicsWorld world;
    private final VehicleRegistry registry;
    private final VehicleFactory factory;
    private final EventBus eventBus;

    private final Matrix4 tmpMat = new Matrix4();
    private final Vector3 tmpSpawn = new Vector3();

    public VehicleBayService(Engine engine, btDiscreteDynamicsWorld world,
                             VehicleRegistry registry, VehicleFactory factory, EventBus eventBus) {
        this.engine = engine;
        this.world = world;
        this.registry = registry;
        this.factory = factory;
        this.eventBus = eventBus;
    }

    /** Spawns a stored vehicle beside the ship ramp. Returns null if it isn't stored/known. */
    public Entity deploy(Entity ship, String vehicleDefinitionId) {
        VehicleBayComponent bay = ship.getComponent(VehicleBayComponent.class);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        if (bay == null || shipTransform == null) return null;
        if (!bay.storedVehicleIds.contains(vehicleDefinitionId)) return null;
        VehicleDefinition def = registry.get(vehicleDefinitionId);
        if (def == null) return null;

        tmpMat.set(shipTransform.position, shipTransform.rotation);
        tmpSpawn.set(bay.localRampSpawnPosition).mul(tmpMat);

        Entity vehicle = factory.create(engine, world, def, tmpSpawn);
        bay.storedVehicleIds.remove(vehicleDefinitionId);
        eventBus.publish(new VehicleDeployedEvent(vehicle, ship));
        return vehicle;
    }

    /** Returns a deployed vehicle to the bay (if capacity allows). Returns false otherwise. */
    public boolean retrieve(Entity ship, Entity vehicle) {
        VehicleBayComponent bay = ship.getComponent(VehicleBayComponent.class);
        VehicleTagComponent tag = vehicle.getComponent(VehicleTagComponent.class);
        if (bay == null || tag == null) return false;
        if (usedSlots(bay) >= bay.capacity) return false;

        PhysicsBodyComponent physics = vehicle.getComponent(PhysicsBodyComponent.class);
        if (physics != null && physics.body != null) {
            world.removeRigidBody(physics.body);
        }
        engine.removeEntity(vehicle);
        bay.storedVehicleIds.add(tag.definitionId);
        eventBus.publish(new VehicleRetrievedEvent(tag.definitionId, ship));
        return true;
    }

    private int usedSlots(VehicleBayComponent bay) {
        int slots = 0;
        for (String id : bay.storedVehicleIds) {
            VehicleDefinition def = registry.get(id);
            slots += (def != null ? Math.max(1, def.baySlots) : 1);
        }
        return slots;
    }
}
