package com.galacticodyssey.water;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.water.components.BallastTankComponent;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.components.FloodableCompartmentComponent;
import com.galacticodyssey.water.components.SubmarineHullComponent;
import com.galacticodyssey.water.components.SubmarineStateComponent;
import com.galacticodyssey.water.components.WaterDragComponent;
import com.galacticodyssey.water.data.CompartmentDefinition;
import com.galacticodyssey.water.data.SubmarineData;

/**
 * Factory for creating submarine entities with all required components.
 * Populates components from data-driven SubmarineData definitions.
 *
 * Usage: call createSubmarine() to get a fully configured entity with
 * hull, ballast, depth control, buoyancy, drag, flooding compartments,
 * and Bullet rigid body already wired up.
 */
public final class SubmarineFactory {

    private SubmarineFactory() {}

    /**
     * Creates a submarine entity from data-driven definition.
     *
     * @param engine       the Ashley engine to add entities to
     * @param dynamicsWorld the Bullet dynamics world for physics
     * @param data         submarine definition loaded from JSON
     * @param spawnPos     initial position in local space
     * @param surfaceLevel Y coordinate of the water surface
     * @return the root submarine entity
     */
    public static Entity createSubmarine(Engine engine, btDiscreteDynamicsWorld dynamicsWorld,
                                          SubmarineData data, Vector3 spawnPos, float surfaceLevel) {
        Entity submarine = new Entity();

        // Transform
        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        submarine.add(transform);

        // Hull
        SubmarineHullComponent hull = new SubmarineHullComponent();
        hull.crushDepth = data.crushDepth;
        hull.depthWarningFraction = data.depthWarningFraction;
        hull.crushDamageRate = data.crushDamageRate;
        hull.displacementVolume = data.displacementVolume;
        hull.dryMass = data.dryMass;
        hull.length = data.length;
        hull.beam = data.beam;
        hull.height = data.height;
        submarine.add(hull);

        // Buoyancy
        BuoyancyComponent buoyancy = new BuoyancyComponent();
        buoyancy.surfaceLevel = surfaceLevel;
        submarine.add(buoyancy);

        // Water drag
        WaterDragComponent drag = new WaterDragComponent();
        drag.forwardDragCoefficient = data.forwardDragCoefficient;
        drag.lateralDragCoefficient = data.lateralDragCoefficient;
        drag.verticalDragCoefficient = data.verticalDragCoefficient;
        drag.addedMassCoefficient = data.addedMassCoefficient;
        drag.angularDragCoefficient = data.angularDragCoefficient;
        // Compute reference areas from hull dimensions
        drag.forwardReferenceArea = data.beam * data.height;
        drag.lateralReferenceArea = data.length * data.height;
        drag.verticalReferenceArea = data.length * data.beam;
        submarine.add(drag);

        // Depth control (PID autopilot)
        DepthControlComponent depthControl = new DepthControlComponent();
        depthControl.kP = data.depthKP;
        depthControl.kI = data.depthKI;
        depthControl.kD = data.depthKD;
        depthControl.maxDescentRate = data.maxDescentRate;
        depthControl.maxAscentRate = data.maxAscentRate;
        submarine.add(depthControl);

        // Ballast tanks
        BallastTankComponent ballast = new BallastTankComponent();
        for (int i = 0; i < data.ballastTanks.size; i++) {
            SubmarineData.BallastTankData tankData = data.ballastTanks.get(i);
            BallastTankComponent.Tank tank = new BallastTankComponent.Tank(
                tankData.id, tankData.capacity, tankData.flowRate,
                tankData.forwardOffset, tankData.verticalOffset);
            ballast.tanks.add(tank);
        }
        submarine.add(ballast);

        // Submarine state
        SubmarineStateComponent state = new SubmarineStateComponent();
        submarine.add(state);

        // Physics body (box shape approximation for the hull)
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(
            data.beam * 0.5f, data.height * 0.5f, data.length * 0.5f));
        physics.mass = data.dryMass;
        physics.friction = 0.3f;
        physics.restitution = 0.1f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnPos));
        physics.body.setFriction(physics.friction);
        physics.body.setRestitution(physics.restitution);
        // Allow rotation but dampen it
        physics.body.setAngularFactor(new Vector3(0.5f, 0.2f, 0.5f));
        physics.body.setDamping(0.1f, 0.3f);
        info.dispose();
        submarine.add(physics);

        dynamicsWorld.addRigidBody(physics.body);

        // Create compartment entities
        for (int i = 0; i < data.compartments.size; i++) {
            CompartmentDefinition compDef = data.compartments.get(i);
            Entity compEntity = createCompartmentEntity(engine, compDef);
            state.compartmentEntities.add(compEntity);
        }

        // Wire up compartment connectivity
        for (int i = 0; i < data.compartments.size; i++) {
            CompartmentDefinition compDef = data.compartments.get(i);
            Entity compEntity = state.compartmentEntities.get(i);
            FloodableCompartmentComponent comp = compEntity.getComponent(FloodableCompartmentComponent.class);

            for (int j = 0; j < compDef.connectedIndices.size; j++) {
                comp.connectedCompartments.add(compDef.connectedIndices.get(j));
                comp.bulkheadIntegrity.add(compDef.initialBulkheadIntegrity);
            }
        }

        engine.addEntity(submarine);
        return submarine;
    }

    /**
     * Creates a default submarine data definition with sensible defaults
     * for testing. In production, this would be loaded from JSON.
     */
    public static SubmarineData createDefaultData() {
        SubmarineData data = new SubmarineData();
        data.id = "default_sub";
        data.name = "Explorer Class";
        data.dryMass = 50000f;
        data.displacementVolume = 120f;
        data.crushDepth = 500f;
        data.depthWarningFraction = 0.85f;
        data.crushDamageRate = 0.05f;
        data.length = 25f;
        data.beam = 6f;
        data.height = 5f;

        // Ballast tanks: fore, aft, and trim tanks
        SubmarineData.BallastTankData foreTank = new SubmarineData.BallastTankData();
        foreTank.id = "fore_ballast";
        foreTank.capacity = 15f;
        foreTank.flowRate = 1.5f;
        foreTank.forwardOffset = 8f;
        foreTank.verticalOffset = -1f;
        data.ballastTanks.add(foreTank);

        SubmarineData.BallastTankData aftTank = new SubmarineData.BallastTankData();
        aftTank.id = "aft_ballast";
        aftTank.capacity = 15f;
        aftTank.flowRate = 1.5f;
        aftTank.forwardOffset = -8f;
        aftTank.verticalOffset = -1f;
        data.ballastTanks.add(aftTank);

        SubmarineData.BallastTankData trimTank = new SubmarineData.BallastTankData();
        trimTank.id = "trim_ballast";
        trimTank.capacity = 5f;
        trimTank.flowRate = 0.8f;
        trimTank.forwardOffset = 0f;
        trimTank.verticalOffset = -1.5f;
        data.ballastTanks.add(trimTank);

        // Compartments: bow, engineering, reactor, crew, stern
        CompartmentDefinition bow = new CompartmentDefinition();
        bow.id = "bow";
        bow.name = "Bow Section";
        bow.volume = 30f;
        bow.forwardOffset = 10f;
        bow.verticalOffset = 0f;
        bow.lateralOffset = 0f;
        bow.connectedIndices.add(1); // connects to engineering
        data.compartments.add(bow);

        CompartmentDefinition engineering = new CompartmentDefinition();
        engineering.id = "engineering";
        engineering.name = "Engineering Bay";
        engineering.volume = 40f;
        engineering.forwardOffset = 4f;
        engineering.verticalOffset = -0.5f;
        engineering.lateralOffset = 0f;
        engineering.connectedIndices.add(0); // bow
        engineering.connectedIndices.add(2); // reactor
        data.compartments.add(engineering);

        CompartmentDefinition reactor = new CompartmentDefinition();
        reactor.id = "reactor";
        reactor.name = "Reactor Compartment";
        reactor.volume = 35f;
        reactor.forwardOffset = 0f;
        reactor.verticalOffset = -1f;
        reactor.lateralOffset = 0f;
        reactor.connectedIndices.add(1); // engineering
        reactor.connectedIndices.add(3); // crew
        data.compartments.add(reactor);

        CompartmentDefinition crew = new CompartmentDefinition();
        crew.id = "crew";
        crew.name = "Crew Quarters";
        crew.volume = 45f;
        crew.forwardOffset = -5f;
        crew.verticalOffset = 0.5f;
        crew.lateralOffset = 0f;
        crew.connectedIndices.add(2); // reactor
        crew.connectedIndices.add(4); // stern
        data.compartments.add(crew);

        CompartmentDefinition stern = new CompartmentDefinition();
        stern.id = "stern";
        stern.name = "Stern Section";
        stern.volume = 25f;
        stern.forwardOffset = -10f;
        stern.verticalOffset = 0f;
        stern.lateralOffset = 0f;
        stern.connectedIndices.add(3); // crew
        data.compartments.add(stern);

        return data;
    }

    private static Entity createCompartmentEntity(Engine engine, CompartmentDefinition def) {
        Entity entity = new Entity();
        FloodableCompartmentComponent comp = new FloodableCompartmentComponent();
        comp.compartmentId = def.id;
        comp.volume = def.volume;
        comp.baseBreachFlowRate = def.baseBreachFlowRate;
        comp.bulkheadFlowRate = def.bulkheadFlowRate;
        comp.forwardOffset = def.forwardOffset;
        comp.verticalOffset = def.verticalOffset;
        comp.lateralOffset = def.lateralOffset;
        entity.add(comp);
        engine.addEntity(entity);
        return entity;
    }
}
