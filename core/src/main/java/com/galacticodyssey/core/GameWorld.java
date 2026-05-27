// core/src/main/java/com/galacticodyssey/core/GameWorld.java
package com.galacticodyssey.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.CoverComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.HostileTagComponent;
import com.galacticodyssey.combat.components.MeleeStateComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.combat.components.SquadComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.AIArchetypeData;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.systems.CombatAISystem;
import com.galacticodyssey.combat.systems.CombatInputSystem;
import com.galacticodyssey.combat.systems.DamageSystem;
import com.galacticodyssey.combat.systems.HitscanSystem;
import com.galacticodyssey.combat.systems.MeleeSystem;
import com.galacticodyssey.combat.systems.ProjectileSystem;
import com.galacticodyssey.combat.systems.SquadTacticsSystem;
import com.galacticodyssey.combat.systems.StatusEffectSystem;
import com.galacticodyssey.combat.systems.WeaponSwitchSystem;
import com.galacticodyssey.combat.systems.WeaponSystem;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.core.systems.PhysicsBodySystem;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.equipment.systems.LootGenerationSystem;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.CrosshairComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerModelComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.RecoilComponent;
import com.galacticodyssey.player.components.ScreenShakeComponent;
import com.galacticodyssey.player.systems.ADSSystem;
import com.galacticodyssey.player.systems.CameraSystem;
import com.galacticodyssey.player.systems.CrosshairSystem;
import com.galacticodyssey.player.systems.InteractionSystem;
import com.galacticodyssey.player.systems.PlayerAnimationSystem;
import com.galacticodyssey.player.systems.PlayerInputSystem;
import com.galacticodyssey.player.systems.PlayerMovementSystem;
import com.galacticodyssey.player.systems.RecoilSystem;
import com.galacticodyssey.player.systems.ScreenShakeSystem;
import com.galacticodyssey.player.systems.WeaponSwaySystem;
import com.galacticodyssey.ship.systems.ShipCameraSystem;
import com.galacticodyssey.ship.systems.ShipFlightSystem;
import com.galacticodyssey.ship.systems.ShipInteriorPhysicsSystem;
import com.galacticodyssey.ship.weapons.data.ShipWeaponRegistry;
import com.galacticodyssey.ship.weapons.systems.PointDefenseSystem;
import com.galacticodyssey.ship.weapons.systems.ShipHeatSystem;
import com.galacticodyssey.ship.weapons.systems.ShipProjectileSystem;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import com.galacticodyssey.ship.weapons.systems.TurretTrackingSystem;
import com.galacticodyssey.ui.systems.DebugHudSystem;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.core.systems.RadialGravitySystem;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.service.TransactionService;
import com.galacticodyssey.economy.simulation.PlanetaryEconomyManager;
import com.galacticodyssey.economy.systems.PlanetaryStockSystem;
import com.galacticodyssey.economy.systems.PricingSystem;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.terrain.PlanetTerrainSystem;
import com.galacticodyssey.planet.terrain.TerrainChunk;
import com.galacticodyssey.vfx.systems.ParticleRenderSystem;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import java.util.List;

public class GameWorld implements Disposable {

    private final Engine engine;
    private final EventBus eventBus;
    private final CoordinateManager coordinateManager;
    private final BulletPhysicsSystem bulletPhysicsSystem;
    private final PhysicsBodySystem physicsBodySystem;
    private final PlayerInputSystem playerInputSystem;
    private final PlayerMovementSystem playerMovementSystem;
    private final CameraSystem cameraSystem;
    private final DebugHudSystem debugHudSystem;
    private final ShipCameraSystem shipCameraSystem;
    private WeaponDataRegistry weaponDataRegistry;
    private CombatDataRegistry combatDataRegistry;
    private EquipmentSystem equipmentSystem;
    private LootGenerationSystem lootGenerationSystem;
    private ShipWeaponSystem shipWeaponSystem;
    private TurretTrackingSystem turretTrackingSystem;
    private ShipProjectileSystem shipProjectileSystem;
    private PointDefenseSystem pointDefenseSystem;
    private ShipHeatSystem shipHeatSystem;
    private ParticleSpawnSystem particleSpawnSystem;
    private ParticleUpdateSystem particleUpdateSystem;
    private ParticleRenderSystem particleRenderSystem;
    private RecoilSystem recoilSystem;
    private ADSSystem adsSystem;
    private CrosshairSystem crosshairSystem;
    private ScreenShakeSystem screenShakeSystem;
    private WeaponSwaySystem weaponSwaySystem;
    private LootTableRegistry lootTableRegistry;
    private ShipWeaponRegistry shipWeaponRegistry;
    private VFXRegistry vfxRegistry;
    private ParticlePoolComponent particlePool;
    private CommodityRegistry commodityRegistry;
    private PlanetEconomyRegistry planetEconomyRegistry;
    private TransactionService transactionService;
    private PlanetaryEconomyManager planetaryEconomyManager;
    private PlanetTerrainSystem planetTerrainSystem;
    private RadialGravitySystem radialGravitySystem;

    private final Array<Disposable> disposables = new Array<>();

    public GameWorld(EventBus eventBus, CoordinateManager coordinateManager) {
        this.engine = new Engine();
        this.eventBus = eventBus;
        this.coordinateManager = coordinateManager;

        bulletPhysicsSystem = new BulletPhysicsSystem(eventBus);
        bulletPhysicsSystem.initialize();

        physicsBodySystem = new PhysicsBodySystem();
        playerInputSystem = new PlayerInputSystem();
        playerMovementSystem = new PlayerMovementSystem(bulletPhysicsSystem.getDynamicsWorld());
        cameraSystem = new CameraSystem(eventBus);
        debugHudSystem = new DebugHudSystem(coordinateManager);

        planetTerrainSystem = new PlanetTerrainSystem(bulletPhysicsSystem.getDynamicsWorld());
        engine.addSystem(planetTerrainSystem);

        radialGravitySystem = new RadialGravitySystem(
            bulletPhysicsSystem.getDynamicsWorld(),
            new Vector3(0, 0, 0), 9.81f);
        engine.addSystem(radialGravitySystem);

        engine.addSystem(playerInputSystem);
        engine.addSystem(playerMovementSystem);
        engine.addSystem(bulletPhysicsSystem);
        engine.addSystem(physicsBodySystem);
        engine.addSystem(cameraSystem);
        engine.addSystem(new PlayerAnimationSystem());
        engine.addSystem(debugHudSystem);

        InteractionSystem interactionSystem = new InteractionSystem(eventBus);
        engine.addSystem(interactionSystem);

        ShipFlightSystem shipFlightSystem = new ShipFlightSystem();
        engine.addSystem(shipFlightSystem);

        ShipInteriorPhysicsSystem interiorPhysicsSystem = new ShipInteriorPhysicsSystem();
        engine.addSystem(interiorPhysicsSystem);

        shipCameraSystem = new ShipCameraSystem();
        engine.addSystem(shipCameraSystem);

        // Combat systems
        WeaponDataRegistry weaponData = new WeaponDataRegistry();
        CombatDataRegistry combatData = new CombatDataRegistry();
        this.weaponDataRegistry = weaponData;
        this.combatDataRegistry = combatData;

        CombatInputSystem combatInputSystem = new CombatInputSystem();
        WeaponSwitchSystem weaponSwitchSystem = new WeaponSwitchSystem(eventBus, weaponData);
        WeaponSystem weaponSystem = new WeaponSystem(eventBus);
        MeleeSystem meleeSystem = new MeleeSystem(eventBus, combatData);
        HitscanSystem hitscanSystem = new HitscanSystem(eventBus);
        ProjectileSystem projectileSystem = new ProjectileSystem(eventBus);
        DamageSystem damageSystem = new DamageSystem(eventBus, combatData, weaponData);
        StatusEffectSystem statusEffectSystem = new StatusEffectSystem(eventBus, combatData);
        CombatAISystem combatAISystem = new CombatAISystem(eventBus);
        SquadTacticsSystem squadTacticsSystem = new SquadTacticsSystem(eventBus);

        engine.addSystem(combatInputSystem);
        engine.addSystem(weaponSwitchSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(meleeSystem);
        engine.addSystem(hitscanSystem);
        engine.addSystem(projectileSystem);
        engine.addSystem(damageSystem);
        engine.addSystem(statusEffectSystem);
        engine.addSystem(combatAISystem);
        engine.addSystem(squadTacticsSystem);

        // Equipment
        lootTableRegistry = new LootTableRegistry();
        equipmentSystem = new EquipmentSystem(eventBus);
        lootGenerationSystem = new LootGenerationSystem(eventBus, lootTableRegistry);
        engine.addSystem(equipmentSystem);
        engine.addSystem(lootGenerationSystem);

        // Ship weapons
        shipWeaponRegistry = new ShipWeaponRegistry();
        // NOTE: loadWeapons/loadHardpointTemplates use Gdx.files.internal() and therefore
        // require the libGDX asset pipeline to be initialized (i.e. after Gdx.app is set up).
        // Calling them here stubs the wiring; they will succeed at runtime but throw in
        // headless unit tests. The actual asset loading is guarded by a null Gdx.files check.
        if (com.badlogic.gdx.Gdx.files != null) {
            shipWeaponRegistry.loadWeapons("data/weapons/ship_weapons.json");
            shipWeaponRegistry.loadHardpointTemplates("data/weapons/hardpoint_templates.json");
        }
        turretTrackingSystem = new TurretTrackingSystem();
        shipWeaponSystem = new ShipWeaponSystem(eventBus);
        shipProjectileSystem = new ShipProjectileSystem(eventBus);
        pointDefenseSystem = new PointDefenseSystem(eventBus);
        shipHeatSystem = new ShipHeatSystem(eventBus);
        engine.addSystem(turretTrackingSystem);
        engine.addSystem(shipWeaponSystem);
        engine.addSystem(shipProjectileSystem);
        engine.addSystem(pointDefenseSystem);
        engine.addSystem(shipHeatSystem);

        // VFX
        // NOTE: VFX effect definitions are loaded from JSON via the asset pipeline
        // (requires Gdx.files / AssetManager). Until that is wired up, effects are
        // registered programmatically in tests or skipped gracefully at runtime when
        // the effectId returned by VFXEventBindings.resolve() is not found in the registry.
        vfxRegistry = new VFXRegistry();
        VFXEventBindings vfxBindings = new VFXEventBindings();
        Entity poolEntity = new Entity();
        particlePool = new ParticlePoolComponent();
        poolEntity.add(particlePool);
        engine.addEntity(poolEntity);
        particleSpawnSystem = new ParticleSpawnSystem(eventBus, vfxRegistry, vfxBindings, particlePool);
        particleUpdateSystem = new ParticleUpdateSystem(particlePool);
        engine.addSystem(particleSpawnSystem);
        engine.addSystem(particleUpdateSystem);

        // Shooting feedback
        recoilSystem = new RecoilSystem(eventBus);
        adsSystem = new ADSSystem();
        crosshairSystem = new CrosshairSystem(eventBus);
        screenShakeSystem = new ScreenShakeSystem(eventBus);
        weaponSwaySystem = new WeaponSwaySystem();
        engine.addSystem(recoilSystem);
        engine.addSystem(adsSystem);
        engine.addSystem(crosshairSystem);
        engine.addSystem(screenShakeSystem);
        engine.addSystem(weaponSwaySystem);

        // Economy
        commodityRegistry = new CommodityRegistry();
        planetEconomyRegistry = new PlanetEconomyRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            commodityRegistry.loadFromFiles();
            planetEconomyRegistry.loadFromFiles();
        }
        PricingSystem pricingSystem = new PricingSystem(commodityRegistry, 5.0f);
        PlanetaryStockSystem planetaryStockSystem = new PlanetaryStockSystem(eventBus);
        engine.addSystem(pricingSystem);
        engine.addSystem(planetaryStockSystem);
        transactionService = new TransactionService(commodityRegistry, eventBus);
        planetaryEconomyManager = new PlanetaryEconomyManager(eventBus, planetEconomyRegistry);

        playerInputSystem.setCombatInputSystem(combatInputSystem);
    }

    public void initializeSystems(PerspectiveCamera camera) {
        playerInputSystem.initialize();
        cameraSystem.setCamera(camera);
        debugHudSystem.initialize();
        shipCameraSystem.setCamera(camera);

        particleRenderSystem = new ParticleRenderSystem(particlePool, camera);
        engine.addSystem(particleRenderSystem);
    }

    public Entity createPlayerEntity(float spawnX, float spawnY, float spawnZ) {
        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnX, spawnY, spawnZ);
        player.add(transform);

        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new MovementStateComponent());
        player.add(new FPSCameraComponent());
        player.add(new PlayerStateComponent());
        player.add(new PlayerModelComponent());

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
        physics.mass = 80f;
        physics.friction = 1.0f;
        physics.restitution = 0f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnX, spawnY, spawnZ));
        physics.body.setAngularFactor(new Vector3(0, 0, 0));
        physics.body.setFriction(physics.friction);
        physics.body.setRestitution(physics.restitution);
        physics.body.setCcdMotionThreshold(0.1f);
        physics.body.setCcdSweptSphereRadius(0.2f);
        info.dispose();

        player.add(physics);

        // Combat components for the player.
        player.add(new CombatInputComponent());
        player.add(new WeaponInventoryComponent());
        player.add(new RangedWeaponComponent());
        player.add(new MeleeWeaponComponent());
        player.add(new MeleeStateComponent());
        player.add(new HealthComponent());
        player.add(new ShieldComponent());
        player.add(new ArmorComponent());
        player.add(new StatusEffectsComponent());
        player.add(new HitboxComponent());

        // Shooting feedback and equipment components.
        player.add(new RecoilComponent());
        player.add(new ADSComponent());
        player.add(new CrosshairComponent());
        player.add(new ScreenShakeComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new EquipmentSlotsComponent());

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(player);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return player;
    }

    public Entity createHostileNPC(Vector3 position, String archetypeId, int squadId,
                                    WeaponDataRegistry weaponData, CombatDataRegistry combatData) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(position);
        entity.add(transform);

        AIArchetypeData archetype = combatData.getArchetype(archetypeId);
        HealthComponent health = new HealthComponent();
        health.maxHP = archetype != null ? archetype.maxHP : 80f;
        health.currentHP = health.maxHP;
        entity.add(health);

        entity.add(new HitboxComponent());
        entity.add(new StatusEffectsComponent());
        entity.add(new HostileTagComponent());
        entity.add(new CombatInputComponent());

        CombatAIComponent ai = new CombatAIComponent();
        if (archetype != null) {
            ai.aggroRange = archetype.aggroRange;
            ai.engageRange = archetype.engageRange;
            ai.preferredRangeMin = archetype.preferredRangeMin;
            ai.preferredRangeMax = archetype.preferredRangeMax;
            ai.aggression = archetype.aggression;
            ai.archetypeId = archetypeId;
        }
        entity.add(ai);

        SquadComponent squad = new SquadComponent();
        squad.squadId = squadId;
        entity.add(squad);
        entity.add(new CoverComponent());

        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        entity.add(inv);
        entity.add(new RangedWeaponComponent());
        entity.add(new MeleeWeaponComponent());
        entity.add(new MeleeStateComponent());

        engine.addEntity(entity);
        return entity;
    }

    public Entity createStaticBox(float x, float y, float z, float halfExtent) {
        return createBox(x, y, z, halfExtent, 0f);
    }

    public Entity createDynamicBox(float x, float y, float z, float halfExtent, float mass) {
        return createBox(x, y, z, halfExtent, mass);
    }

    private Entity createBox(float x, float y, float z, float halfExtent, float mass) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(halfExtent, halfExtent, halfExtent));
        physics.mass = mass;

        Vector3 inertia = new Vector3();
        if (mass > 0) {
            physics.shape.calculateLocalInertia(mass, inertia);
        }
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        physics.body.setFriction(0.8f);
        info.dispose();

        entity.add(physics);

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(entity);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return entity;
    }

    public void addTerrainBody(btRigidBody terrainBody) {
        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(terrainBody);
        bulletPhysicsSystem.addManagedBody(terrainBody);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(terrainBody);
            terrainBody.dispose();
        });
    }

    public void update(float delta) {
        engine.update(delta);

        Entity player = engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get()).first();
        TransformComponent t = player.getComponent(TransformComponent.class);
        coordinateManager.checkRebase(t.position);
    }

    public void resize(int width, int height) {
        debugHudSystem.resize(width, height);
    }

    public Engine getEngine() { return engine; }
    public EventBus getEventBus() { return eventBus; }

    public BulletPhysicsSystem getBulletPhysicsSystem() {
        return bulletPhysicsSystem;
    }

    public PlayerInputSystem getPlayerInputSystem() {
        return playerInputSystem;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        planetTerrainSystem.loadPlanet(planet, biomeMap);
    }

    public PlanetTerrainSystem getPlanetTerrainSystem() {
        return planetTerrainSystem;
    }

    public List<TerrainChunk> getVisibleTerrainLeaves() {
        return planetTerrainSystem.getVisibleLeaves();
    }

    public float getPlanetRadius() {
        return planetTerrainSystem.getPlanetRadius();
    }

    @Override
    public void dispose() {
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        if (particleRenderSystem != null) {
            particleRenderSystem.dispose();
        }
        if (particlePool != null) {
            particlePool.freeAll();
        }
        if (planetTerrainSystem != null) {
            planetTerrainSystem.dispose();
        }
        debugHudSystem.dispose();
        bulletPhysicsSystem.dispose();
    }
}
