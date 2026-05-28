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
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponStatsResolver;
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
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.systems.ADSSystem;
import com.galacticodyssey.player.systems.CameraSystem;
import com.galacticodyssey.player.systems.CrosshairSystem;
import com.galacticodyssey.player.systems.InteractionSystem;
import com.galacticodyssey.player.systems.PlayerAnimationSystem;
import com.galacticodyssey.player.systems.PlayerInputSystem;
import com.galacticodyssey.player.systems.PlayerMovementSystem;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import com.galacticodyssey.player.systems.RecoilSystem;
import com.galacticodyssey.player.systems.ScreenShakeSystem;
import com.galacticodyssey.player.systems.WeaponSwaySystem;
import com.galacticodyssey.player.systems.PilotTransitionSystem;
import com.galacticodyssey.ship.data.ShipClassRegistry;
import com.galacticodyssey.ship.systems.AtmosphericFlightSystem;
import com.galacticodyssey.ship.systems.CockpitModelSystem;
import com.galacticodyssey.ship.systems.ShipCameraSystem;
import com.galacticodyssey.ship.systems.ShipFlightSystem;
import com.galacticodyssey.ship.systems.ShipInteriorPhysicsSystem;
import com.galacticodyssey.ship.weapons.data.ShipWeaponRegistry;
import com.galacticodyssey.ship.weapons.systems.PointDefenseSystem;
import com.galacticodyssey.ship.weapons.systems.ShipHeatSystem;
import com.galacticodyssey.ship.weapons.systems.ShipProjectileSystem;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponPilotSystem;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import com.galacticodyssey.ship.weapons.systems.TargetingSystem;
import com.galacticodyssey.ship.weapons.systems.TurretTrackingSystem;
import com.galacticodyssey.galaxy.KeplerianOrbitSystem;
import com.galacticodyssey.ship.systems.RelativisticDopplerSystem;
import com.galacticodyssey.ship.systems.VelocityTimeDilationSystem;
import com.galacticodyssey.ui.CockpitHUDSystem;
import com.galacticodyssey.ship.flooding.systems.FloodingHudSystem;
import com.galacticodyssey.ship.flooding.systems.ShipFloodingSystem;
import com.galacticodyssey.ui.systems.DebugHudSystem;
import com.galacticodyssey.water.DepthZoneComponent;
import com.galacticodyssey.water.OceanSpawner;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.systems.BallastSystem;
import com.galacticodyssey.water.systems.BoatBuoyancySystem;
import com.galacticodyssey.water.systems.BoatMotorSystem;
import com.galacticodyssey.water.systems.DeckWashSystem;
import com.galacticodyssey.water.systems.HatchFloodingSystem;
import com.galacticodyssey.water.systems.HullIntegritySystem;
import com.galacticodyssey.water.systems.HydrodynamicDragSystem;
import com.galacticodyssey.water.systems.SwimCameraSystem;
import com.galacticodyssey.water.systems.SwimmingSystem;
import com.galacticodyssey.water.systems.UnderwaterSystem;
import com.galacticodyssey.water.systems.WakeTrailSystem;
import com.galacticodyssey.water.systems.WaveSystem;
import com.galacticodyssey.water.systems.WeatherSystem;
import com.galacticodyssey.water.data.VesselRegistry;
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.VesselFactory;
import com.galacticodyssey.combat.systems.BulletTracerSystem;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.data.VFXLoader;
import com.galacticodyssey.vfx.ParticleAtlasManager;
import com.galacticodyssey.vfx.MeshParticlePool;
import com.galacticodyssey.core.systems.RadialGravitySystem;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.service.TransactionService;
import com.galacticodyssey.economy.simulation.PlanetaryEconomyManager;
import com.galacticodyssey.economy.systems.PlanetaryStockSystem;
import com.galacticodyssey.economy.systems.PricingSystem;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.terrain.DustSystem;
import com.galacticodyssey.planet.terrain.PlanetTerrainSystem;
import com.galacticodyssey.planet.terrain.SeismicSystem;
import com.galacticodyssey.planet.terrain.SurfaceAnchorSystem;
import com.galacticodyssey.planet.terrain.SurfaceVehicleSystem;
import com.galacticodyssey.planet.terrain.TerrainChunk;
import com.galacticodyssey.audio.AudioSystem;
import com.galacticodyssey.vfx.systems.ParticleRenderSystem;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import com.galacticodyssey.persistence.LocalFileSaveBackend;
import com.galacticodyssey.persistence.PersistenceIdComponent;
import com.galacticodyssey.persistence.SaveCoordinator;
import com.galacticodyssey.data.GalacticAssetManager;
import com.galacticodyssey.data.systems.StreamingSystem;
import com.badlogic.gdx.utils.JsonReader;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.job.ProceduralJobGenerator;
import com.galacticodyssey.mission.job.EventJobGenerator;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.mission.saga.SagaRunner;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.shared.ObjectiveTrackingSystem;
import com.galacticodyssey.mission.shared.RewardSystem;
import com.galacticodyssey.mission.discovery.QuestDiscoverySystem;
import com.galacticodyssey.npc.components.NpcDialogComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.data.DialogDataRegistry;
import com.galacticodyssey.npc.data.NpcDataRegistry;
import com.galacticodyssey.npc.data.NpcGenerator;
import com.galacticodyssey.npc.data.RecruitmentDataRegistry;
import com.galacticodyssey.npc.systems.CandidatePoolSystem;
import com.galacticodyssey.npc.systems.DialogSystem;
import com.galacticodyssey.ship.power.PowerPenaltySystem;
import com.galacticodyssey.ship.power.PowerSystem;
import com.galacticodyssey.ship.power.ReactorSpecRegistry;
import com.galacticodyssey.stealth.BulletLineOfSightQuery;
import com.galacticodyssey.stealth.NpcAwarenessSystem;
import com.galacticodyssey.stealth.ShipDetectionSystem;
import com.galacticodyssey.stealth.ShipSignatureSystem;
import com.galacticodyssey.stealth.SignatureComponent;
import com.galacticodyssey.hacking.HackingStateComponent;
import com.galacticodyssey.hacking.data.HackableTypeRegistry;
import com.galacticodyssey.hacking.systems.HackingSystem;
import com.galacticodyssey.hacking.systems.PlayerHackingSystem;
import com.galacticodyssey.rendering.lighting.LightingSystem;
import java.io.File;
import java.util.List;
import java.util.UUID;

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
    private ParticleAtlasManager particleAtlasManager;
    private MeshParticlePool meshParticlePool;
    private RecoilSystem recoilSystem;
    private ADSSystem adsSystem;
    private CrosshairSystem crosshairSystem;
    private ScreenShakeSystem screenShakeSystem;
    private WeaponSwaySystem weaponSwaySystem;
    private LootTableRegistry lootTableRegistry;
    private ShipWeaponRegistry shipWeaponRegistry;
    private ShipClassRegistry shipClassRegistry;
    private CockpitModelSystem cockpitModelSystem;
    private CockpitHUDSystem cockpitHUDSystem;
    private KeplerianOrbitSystem keplerianOrbitSystem;
    private VFXRegistry vfxRegistry;
    private ParticlePoolComponent particlePool;
    private CommodityRegistry commodityRegistry;
    private PlanetEconomyRegistry planetEconomyRegistry;
    private TransactionService transactionService;
    private PlanetaryEconomyManager planetaryEconomyManager;
    private PlanetTerrainSystem planetTerrainSystem;
    private RadialGravitySystem radialGravitySystem;
    private SurfaceVehicleSystem surfaceVehicleSystem;
    private DustSystem dustSystem;
    private SeismicSystem seismicSystem;
    private SurfaceAnchorSystem surfaceAnchorSystem;
    private AudioSystem audioSystem;
    private RealTimeSkillSystem realTimeSkillSystem;
    private WaveSystem waveSystem;
    private WaterDataRegistry waterDataRegistry;
    private OceanSpawner oceanSpawner;
    private VesselRegistry vesselRegistry;
    private VesselFactory vesselFactory;
    private UUID playerEntityId;
    private SaveCoordinator saveCoordinator;
    private JobRegistry jobRegistry;
    private SagaRegistry sagaRegistry;
    private ProceduralJobGenerator proceduralJobGenerator;
    private EventJobGenerator eventJobGenerator;
    private QuestJournal questJournal;
    private ObjectiveTrackingSystem objectiveTrackingSystem;
    private SagaRunner sagaRunner;
    private RewardSystem rewardSystem;
    private QuestDiscoverySystem questDiscoverySystem;
    private GalacticAssetManager assetManager;
    private StreamingSystem streamingSystem;
    private com.badlogic.gdx.graphics.PerspectiveCamera camera;
    private DialogDataRegistry dialogDataRegistry;
    private DialogSystem dialogSystem;
    private CandidatePoolSystem candidatePoolSystem;
    private NpcAwarenessSystem npcAwarenessSystem;
    private ShipDetectionSystem shipDetectionSystem;
    private ShipSignatureSystem shipSignatureSystem;
    private HackableTypeRegistry hackableTypeRegistry;
    private PlayerHackingSystem playerHackingSystem;
    private ReactorSpecRegistry reactorSpecRegistry;
    private PowerSystem powerSystem;
    private LightingSystem lightingSystem;

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
        debugHudSystem = new DebugHudSystem(coordinateManager, eventBus);

        planetTerrainSystem = new PlanetTerrainSystem(bulletPhysicsSystem.getDynamicsWorld());
        engine.addSystem(planetTerrainSystem);

        // Planet center placed 50 km below terrain so radial gravity is effectively
        // straight-down everywhere on the 500m×500m flat test map (<0.5° tilt at corners).
        // Both systems must share the same center or movement/gravity will diverge.
        Vector3 planetCenter = new Vector3(0, -50000f, 0);
        radialGravitySystem = new RadialGravitySystem(
            bulletPhysicsSystem.getDynamicsWorld(),
            planetCenter, 9.81f);
        engine.addSystem(radialGravitySystem);
        playerMovementSystem.setPlanetCenter(planetCenter);

        // Surface vehicle physics — pass null for GravitySystem: RadialGravitySystem
        // already applies gravity forces directly; SurfaceVehicleSystem falls back to 9.81 m/s².
        surfaceVehicleSystem = new SurfaceVehicleSystem(null, eventBus);
        dustSystem = new DustSystem(eventBus);
        seismicSystem = new SeismicSystem(eventBus);
        surfaceAnchorSystem = new SurfaceAnchorSystem(eventBus);
        engine.addSystem(surfaceVehicleSystem);
        engine.addSystem(dustSystem);
        engine.addSystem(seismicSystem);
        engine.addSystem(surfaceAnchorSystem);

        realTimeSkillSystem = new RealTimeSkillSystem(eventBus);
        engine.addSystem(realTimeSkillSystem);
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
        engine.addSystem(new VelocityTimeDilationSystem(eventBus));
        engine.addSystem(new RelativisticDopplerSystem(eventBus));

        PilotTransitionSystem pilotTransitionSystem = new PilotTransitionSystem(eventBus);
        engine.addSystem(pilotTransitionSystem);

        AtmosphericFlightSystem atmosphericFlightSystem = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(atmosphericFlightSystem);

        ShipInteriorPhysicsSystem interiorPhysicsSystem = new ShipInteriorPhysicsSystem();
        engine.addSystem(interiorPhysicsSystem);

        shipCameraSystem = new ShipCameraSystem();
        engine.addSystem(shipCameraSystem);

        // Combat systems
        WeaponDataRegistry weaponData = new WeaponDataRegistry();
        CombatDataRegistry combatData = new CombatDataRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            try {
                weaponData.loadFromFiles();
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load weapon data", e);
            }
            try {
                combatData.loadFromFiles();
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load combat data", e);
            }
        }
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

        lightingSystem = new LightingSystem();
        engine.addSystem(lightingSystem);

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

        ShipWeaponPilotSystem shipWeaponPilotSystem = new ShipWeaponPilotSystem(shipWeaponSystem);
        engine.addSystem(shipWeaponPilotSystem);

        cockpitModelSystem = new CockpitModelSystem(eventBus);
        engine.addSystem(cockpitModelSystem);

        cockpitHUDSystem = new CockpitHUDSystem(eventBus);
        engine.addSystem(cockpitHUDSystem);

        keplerianOrbitSystem = new KeplerianOrbitSystem(eventBus);
        engine.addSystem(keplerianOrbitSystem);

        shipClassRegistry = new ShipClassRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            shipClassRegistry.loadShipClasses("data/ships/ship_classes.json");
            shipClassRegistry.loadAtmosphereProfiles("data/planets/atmosphere_profiles.json");
        }

        // Power management
        reactorSpecRegistry = new ReactorSpecRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            try {
                reactorSpecRegistry.loadFromFile("data/power/reactor_specs.json");
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load reactor specs", e);
            }
        }
        powerSystem = new PowerSystem(eventBus);
        engine.addSystem(powerSystem);
        engine.addSystem(new PowerPenaltySystem());

        // Water / Hydrodynamics
        waveSystem = new WaveSystem(10);
        engine.addSystem(waveSystem);
        engine.addSystem(new BoatBuoyancySystem(coordinateManager, waveSystem));
        engine.addSystem(new HydrodynamicDragSystem(12, waveSystem));
        engine.addSystem(new BallastSystem(13, coordinateManager, waveSystem, eventBus));
        engine.addSystem(new com.galacticodyssey.water.systems.FloodingSystem(14, waveSystem, eventBus));
        engine.addSystem(new HullIntegritySystem(eventBus));
        engine.addSystem(new BoatMotorSystem());
        engine.addSystem(new WakeTrailSystem());
        engine.addSystem(new ShipFloodingSystem(eventBus));
        engine.addSystem(new FloodingHudSystem(eventBus));

        // Swimming & water mechanics
        waterDataRegistry = new WaterDataRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            waterDataRegistry.loadFromFiles();
        } else {
            waterDataRegistry.setSwimConfig(new com.galacticodyssey.water.data.SwimConfigData());
        }

        WeatherSystem weatherSystem = new WeatherSystem(5, eventBus, waterDataRegistry);
        engine.addSystem(weatherSystem);

        DeckWashSystem deckWashSystem = new DeckWashSystem(17, eventBus);
        deckWashSystem.setWaveSystem(waveSystem);
        engine.addSystem(deckWashSystem);

        HatchFloodingSystem hatchFloodingSystem = new HatchFloodingSystem(18, eventBus);
        engine.addSystem(hatchFloodingSystem);

        SwimmingSystem swimmingSystem = new SwimmingSystem(20, eventBus, waterDataRegistry);
        swimmingSystem.setWaveSystem(waveSystem);
        engine.addSystem(swimmingSystem);

        UnderwaterSystem underwaterSystem = new UnderwaterSystem(21, eventBus, waterDataRegistry);
        engine.addSystem(underwaterSystem);

        SwimCameraSystem swimCameraSystem = new SwimCameraSystem(90, waterDataRegistry);
        swimCameraSystem.setWaveSystem(waveSystem);
        engine.addSystem(swimCameraSystem);

        oceanSpawner = new OceanSpawner(engine, waveSystem);

        vesselRegistry = new VesselRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            vesselRegistry.loadVessels("data/vessels/vessel_hulls.json");
        }
        vesselFactory = new VesselFactory(engine, bulletPhysicsSystem,
                radialGravitySystem != null ? radialGravitySystem.getGravityMagnitude() : 9.81f);

        // VFX
        // NOTE: VFX effect definitions are loaded from JSON via the asset pipeline
        // (requires Gdx.files / AssetManager). Until that is wired up, effects are
        // registered programmatically in tests or skipped gracefully at runtime when
        // the effectId returned by VFXEventBindings.resolve() is not found in the registry.
        vfxRegistry = new VFXRegistry();
        VFXEventBindings vfxBindings = new VFXEventBindings();
        if (com.badlogic.gdx.Gdx.files != null) {
            VFXLoader.loadAll(vfxRegistry, vfxBindings);
        }
        Entity poolEntity = new Entity();
        particlePool = new ParticlePoolComponent();
        poolEntity.add(particlePool);
        engine.addEntity(poolEntity);
        particleSpawnSystem = new ParticleSpawnSystem(eventBus, vfxRegistry, vfxBindings, particlePool);
        particleUpdateSystem = new ParticleUpdateSystem(particlePool);
        engine.addSystem(particleSpawnSystem);
        engine.addSystem(particleUpdateSystem);

        BulletTracerSystem bulletTracerSystem = new BulletTracerSystem(eventBus, particlePool);
        engine.addSystem(bulletTracerSystem);

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

        // Mission / Quest System
        questJournal = new QuestJournal();
        jobRegistry = new JobRegistry();
        sagaRegistry = new SagaRegistry();
        proceduralJobGenerator = new ProceduralJobGenerator(jobRegistry);
        eventJobGenerator = new EventJobGenerator(eventBus, jobRegistry, proceduralJobGenerator);
        questDiscoverySystem = new QuestDiscoverySystem(eventBus, questJournal);
        objectiveTrackingSystem = new ObjectiveTrackingSystem(eventBus, questJournal);
        sagaRunner = new SagaRunner(eventBus, questJournal, sagaRegistry);
        rewardSystem = new RewardSystem(eventBus);

        eventJobGenerator.setJobListener(job -> {
            if (job.lead != null) questDiscoverySystem.registerLead(job, job.lead);
            questJournal.addRumour(job);
        });

        engine.addSystem(objectiveTrackingSystem);
        engine.addSystem(sagaRunner);

        // Stealth System
        BulletLineOfSightQuery losQuery = new BulletLineOfSightQuery(bulletPhysicsSystem.getDynamicsWorld());
        disposables.add(losQuery);
        npcAwarenessSystem  = new NpcAwarenessSystem(eventBus, losQuery);
        shipSignatureSystem = new ShipSignatureSystem();
        shipDetectionSystem = new ShipDetectionSystem(eventBus);
        engine.addSystem(npcAwarenessSystem);
        engine.addSystem(shipSignatureSystem);
        engine.addSystem(shipDetectionSystem);

        // Dialog system
        dialogDataRegistry = new DialogDataRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            try {
                dialogDataRegistry.loadFromFiles();
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load dialog data", e);
            }
        }
        dialogSystem = new DialogSystem(eventBus, dialogDataRegistry);
        engine.addSystem(dialogSystem);

        // Recruitment systems
        NpcDataRegistry npcDataRegistry = new NpcDataRegistry();
        RecruitmentDataRegistry recruitmentDataRegistry = new RecruitmentDataRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            try {
                npcDataRegistry.loadFromFiles();
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load NPC data", e);
            }
            try {
                recruitmentDataRegistry.loadFromFiles();
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameWorld", "Failed to load recruitment data", e);
            }
        }
        candidatePoolSystem = new CandidatePoolSystem(
            eventBus, new NpcGenerator(npcDataRegistry), recruitmentDataRegistry);
        engine.addSystem(candidatePoolSystem);

        // Hacking systems
        hackableTypeRegistry = new HackableTypeRegistry();
        if (com.badlogic.gdx.Gdx.files != null) {
            hackableTypeRegistry.loadFromFiles();
        }
        playerHackingSystem = new PlayerHackingSystem(eventBus);
        engine.addSystem(playerHackingSystem);
        engine.addSystem(new HackingSystem(25, eventBus));

        // Asset streaming
        if (com.badlogic.gdx.Gdx.files != null) {
            assetManager = new GalacticAssetManager();
            JsonReader jsonReader = new JsonReader();
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/characters.json")));
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/props.json")));
            assetManager.loadStreamingConfig(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/streaming_config.json")));
            streamingSystem = new StreamingSystem(assetManager);
            engine.addSystem(streamingSystem);
        }

        playerInputSystem.setCombatInputSystem(combatInputSystem);
    }

    public void initializeSystems(PerspectiveCamera camera) {
        this.camera = camera;
        playerInputSystem.initialize();
        cameraSystem.setCamera(camera);
        debugHudSystem.initialize();
        shipCameraSystem.setCamera(camera);
        cockpitHUDSystem.initialize();

        TargetingSystem targetingSystem = new TargetingSystem(eventBus, camera);
        engine.addSystem(targetingSystem);

        particleRenderSystem = new ParticleRenderSystem(particlePool, camera);
        engine.addSystem(particleRenderSystem);
        particleAtlasManager = new ParticleAtlasManager();
        particleAtlasManager.generate();
        meshParticlePool = new MeshParticlePool();
        particleSpawnSystem.setAtlasManager(particleAtlasManager);
        particleRenderSystem.setAtlasManager(particleAtlasManager);
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        com.badlogic.gdx.graphics.g3d.Model fallbackModel = mb.createBox(0.05f, 0.05f, 0.05f,
            new com.badlogic.gdx.graphics.g3d.Material(),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
        com.badlogic.gdx.graphics.g3d.ModelInstance fallbackMesh = new com.badlogic.gdx.graphics.g3d.ModelInstance(fallbackModel);
        com.badlogic.gdx.graphics.g3d.ModelBatch meshModelBatch = new com.badlogic.gdx.graphics.g3d.ModelBatch();
        particleRenderSystem.setMeshParticlePool(meshParticlePool, meshModelBatch, fallbackMesh);
    }

    public Entity createPlayerEntity(float spawnX, float spawnY, float spawnZ) {
        Entity player = new Entity();

        PersistenceIdComponent pid = new PersistenceIdComponent();
        player.add(pid);
        this.playerEntityId = pid.uuid;

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnX, spawnY, spawnZ);
        player.add(transform);

        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new MovementStateComponent());
        player.add(new FPSCameraComponent());
        player.add(new PlayerStateComponent());
        player.add(new PlayerModelComponent());
        player.add(new SwimmingStateComponent());
        player.add(new DepthZoneComponent());
        player.add(new HackingStateComponent());

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

        player.add(new PlayerStatsComponent());
        player.add(new SignatureComponent());

        // Shooting feedback and equipment components.
        player.add(new RecoilComponent());
        player.add(new ADSComponent());
        player.add(new CrosshairComponent());
        player.add(new ScreenShakeComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new EquipmentSlotsComponent());

        equipStarterWeapons(player);

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

    private void equipStarterWeapons(Entity player) {
        WeaponInventoryComponent inventory = player.getComponent(WeaponInventoryComponent.class);
        RangedWeaponComponent ranged = player.getComponent(RangedWeaponComponent.class);
        if (inventory == null || ranged == null) return;

        WeaponAssembly assaultRifle = WeaponAssembly.ranged(
            "assault_rifle", "standard_barrel", "standard_round", null,
            com.galacticodyssey.combat.CombatEnums.QualityTier.COMMON);
        inventory.slots[0] = assaultRifle;
        inventory.activeSlotIndex = 0;

        WeaponAssembly sidearm = WeaponAssembly.ranged(
            "pistol_standard", "standard_barrel", "standard_round", null,
            com.galacticodyssey.combat.CombatEnums.QualityTier.COMMON);
        inventory.slots[1] = sidearm;

        WeaponAssembly melee = WeaponAssembly.melee("combat_blade",
            com.galacticodyssey.combat.CombatEnums.QualityTier.COMMON);
        inventory.slots[2] = melee;

        if (weaponDataRegistry != null && weaponDataRegistry.getFrame("assault_rifle") != null) {
            WeaponStatsResolver.RangedStats stats =
                WeaponStatsResolver.resolveRanged(assaultRifle, weaponDataRegistry);
            ranged.damage = stats.damage;
            ranged.fireRate = stats.fireRate;
            ranged.spread = stats.spread;
            ranged.range = stats.range;
            ranged.recoil = stats.recoil;
            ranged.magSize = stats.magSize;
            ranged.currentAmmo = stats.magSize;
            ranged.reloadTime = stats.reloadTime;
            ranged.firingMode = stats.firingMode;
            ranged.hitscan = stats.hitscan;
            ranged.damageType = stats.damageType;
            ranged.statusEffect = stats.statusEffect;
            ranged.statusEffectChance = stats.statusEffectChance;
            ranged.projectileSpeed = stats.projectileSpeed;
            ranged.ammoTypeId = stats.ammoTypeId;
        }
    }

    public Entity createHostileNPC(Vector3 position, String archetypeId, int squadId,
                                    WeaponDataRegistry weaponData, CombatDataRegistry combatData) {
        Entity entity = new Entity();
        entity.add(new PersistenceIdComponent());
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
        if (assetManager != null) {
            if (camera != null) streamingSystem.setCameraPosition(camera.position);
            assetManager.update();
        }
        engine.update(delta);

        if (saveCoordinator != null) {
            saveCoordinator.update(delta);
        }

        if (planetTerrainSystem == null || planetTerrainSystem.getPlanetRadius() <= 0) {
            Entity player = engine.getEntitiesFor(
                com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get()).first();
            TransformComponent t = player.getComponent(TransformComponent.class);
            coordinateManager.checkRebase(t.position);
        }
    }

    public void resize(int width, int height) {
        debugHudSystem.resize(width, height);
        cockpitHUDSystem.resize(width, height);
    }

    public Entity spawnTestNpc(float x, float y, float z) {
        Entity npc = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        npc.add(transform);

        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.npcId = "test_merchant";
        identity.name = "Zara Voss";
        identity.role = com.galacticodyssey.npc.NPCRole.MERCHANT;
        npc.add(identity);

        NpcDialogComponent dialog = new NpcDialogComponent();
        dialog.dialogTreeId = "test_merchant";
        dialog.interactionRadius = 3f;
        npc.add(dialog);

        engine.addEntity(npc);
        return npc;
    }

    public DialogSystem getDialogSystem() { return dialogSystem; }
    public DialogDataRegistry getDialogDataRegistry() { return dialogDataRegistry; }
    public HackableTypeRegistry getHackableTypeRegistry() { return hackableTypeRegistry; }
    public ReactorSpecRegistry getReactorSpecRegistry() { return reactorSpecRegistry; }
    public PowerSystem getPowerSystem() { return powerSystem; }
    public EquipmentSystem getEquipmentSystem() { return equipmentSystem; }
    public QuestJournal getQuestJournal() { return questJournal; }
    public JobRegistry getJobRegistry() { return jobRegistry; }
    public SagaRegistry getSagaRegistry() { return sagaRegistry; }
    public CandidatePoolSystem getCandidatePoolSystem() { return candidatePoolSystem; }

    /** Wire up the audio system. Call after GameWorld construction, before the first update(). */
    public void initAudio(AudioManager audioManager) {
        audioSystem = new AudioSystem(eventBus, audioManager);
    }

    /**
     * Initialize the save/load system. Must be called after {@link #createPlayerEntity} so
     * that {@code playerEntityId} is available.
     *
     * @param galaxySeed seed embedded in every save manifest
     * @throws IllegalStateException if the player entity has not been created yet
     */
    public void initSaveSystem(long galaxySeed) {
        if (playerEntityId == null) {
            throw new IllegalStateException("Player entity must be created before initializing save system");
        }
        File savesDir;
        if (com.badlogic.gdx.Gdx.files != null) {
            savesDir = com.badlogic.gdx.Gdx.files.external("GalacticOdyssey/saves").file();
        } else {
            savesDir = new File(System.getProperty("user.home"), "GalacticOdyssey/saves");
        }
        LocalFileSaveBackend saveBackend = new LocalFileSaveBackend(savesDir);
        this.saveCoordinator = new SaveCoordinator(
            eventBus, engine, saveBackend, galaxySeed, playerEntityId, coordinateManager);
    }

    public AudioSystem getAudioSystem() { return audioSystem; }

    public OceanSpawner getOceanSpawner() { return oceanSpawner; }

    public VesselRegistry getVesselRegistry() { return vesselRegistry; }

    public VesselFactory getVesselFactory() { return vesselFactory; }

    public Engine getEngine() { return engine; }
    public EventBus getEventBus() { return eventBus; }

    public BulletPhysicsSystem getBulletPhysicsSystem() {
        return bulletPhysicsSystem;
    }

    public PlayerInputSystem getPlayerInputSystem() {
        return playerInputSystem;
    }

    public DebugHudSystem getDebugHudSystem() {
        return debugHudSystem;
    }

    public CockpitHUDSystem getCockpitHUDSystem() {
        return cockpitHUDSystem;
    }

    public CockpitModelSystem getCockpitModelSystem() {
        return cockpitModelSystem;
    }

    public KeplerianOrbitSystem getKeplerianOrbitSystem() {
        return keplerianOrbitSystem;
    }

    public ParticleRenderSystem getParticleRenderSystem() {
        return particleRenderSystem;
    }

    public LightingSystem getLightingSystem() { return lightingSystem; }

    @Override
    public void dispose() {
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        if (particleAtlasManager != null) particleAtlasManager.dispose();
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
        if (cockpitHUDSystem != null) cockpitHUDSystem.dispose();
        if (cockpitModelSystem != null) cockpitModelSystem.dispose();
        if (assetManager != null) assetManager.dispose();
        bulletPhysicsSystem.dispose();
    }
}
