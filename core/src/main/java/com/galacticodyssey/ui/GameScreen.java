package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.core.GameWorld;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.WorldPopulator;
import com.galacticodyssey.flora.alien.AlienPlantGenerator;
import com.galacticodyssey.flora.alien.AlienPlantRegistry;
import com.galacticodyssey.flora.grass.GrassField;
import com.galacticodyssey.flora.grass.GrassRenderer;
import com.galacticodyssey.flora.grass.GrassRegistry;
import com.galacticodyssey.flora.grass.HeightmapTerrainSampler;
import com.galacticodyssey.rendering.EmissiveGBufferShaderProvider;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipFactory;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipMeshComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.galacticodyssey.npc.events.DialogClosedEvent;
import com.galacticodyssey.npc.events.DialogOpenedEvent;
import com.galacticodyssey.npc.systems.DialogSystem;
import com.galacticodyssey.ui.systems.DialogHudSystem;
import com.galacticodyssey.ui.systems.InventoryScreenSystem;
import com.galacticodyssey.ui.systems.RecruitmentScreenSystem;
import com.galacticodyssey.ui.outfitter.OutfitterScreenSystem;
import com.galacticodyssey.ui.events.OutfitterOpenedEvent;
import com.galacticodyssey.ui.events.OutfitterClosedEvent;
import com.galacticodyssey.ui.events.JournalClosedEvent;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
import com.galacticodyssey.hacking.ui.HackingOverlay;
import com.galacticodyssey.hacking.HackingStateComponent;
import com.galacticodyssey.hacking.events.HackStartedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.rendering.DeferredRenderer;
import com.galacticodyssey.rendering.GBufferBatchShaderProvider;
import com.galacticodyssey.rendering.lighting.LightComponent;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;
import com.galacticodyssey.npc.events.RecruitmentClosedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureGenerator;
import com.galacticodyssey.fauna.CreatureMeshBuilder;
import com.galacticodyssey.fauna.FaunaDebugSpawner;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;
import com.galacticodyssey.fauna.geometry.ProceduralPartProvider;
import com.galacticodyssey.fauna.geometry.AuthoredPartProvider;

import java.util.Random;

public class GameScreen implements Screen {

    private static final int TERRAIN_VERTS_X = 257;
    private static final int TERRAIN_VERTS_Z = 257;
    private static final float TERRAIN_WIDTH = 500f;
    private static final float TERRAIN_DEPTH = 500f;
    private static final long TERRAIN_SEED = 42L;
    private static final float PAUSE_WORLD_WIDTH = 1280f;
    private static final float PAUSE_WORLD_HEIGHT = 720f;

    private final GalacticOdyssey game;
    private final GameSession session; // null in load-game flow
    private GameWorld gameWorld;
    private PerspectiveCamera camera;
    private float[] heightmap;

    private ShipFactory shipFactory;
    private final Array<Entity> shipEntities = new Array<>();
    private ShaderProgram shipShader;

    private Mesh terrainMesh;
    private ShaderProgram terrainShader;
    private ModelBatch modelBatch;
    private ModelBatch gbufferBatch;
    private com.badlogic.gdx.graphics.g3d.ModelBatch creatureBatch;
    private Environment environment;
    private final Matrix4 tmpMat4 = new Matrix4();
    private final Matrix4 tmpViewModelMat4 = new Matrix4();
    private final Matrix3 tmpNormalMat3 = new Matrix3();
    private final Array<ModelInstance> boxInstances = new Array<>();
    private final Array<Entity> boxEntities = new Array<>();
    private final Array<Disposable> disposables = new Array<>();

    private WorldPopulator.PopulatedWorld populatedWorld;

    private AtmosphericSkyRenderer atmosphericSkyRenderer;
    private DeferredRenderer deferredRenderer;
    private DayNightCycle dayNightCycle;
    private float gameTime;

    private GrassField grassField;
    private GrassRenderer grassRenderer;
    private ModelBatch alienBatch;
    private static final int GRASS_MAX_INSTANCES = 200_000;

    private boolean paused;
    private Stage pauseStage;
    private Texture overlayTexture;
    private InputMultiplexer inputMultiplexer = new InputMultiplexer();
    private boolean initialized;

    private Model fpWeaponModel;
    private ModelInstance fpWeaponInstance;
    private float weaponBobTimer;
    private float weaponSwayX;
    private float weaponSwayY;

    private Model vehicleFallbackModel;
    private final java.util.Map<com.badlogic.ashley.core.Entity, com.badlogic.gdx.graphics.g3d.ModelInstance> vehicleInstances =
        new java.util.HashMap<>();

    // Muzzle flash screen-space overlay
    private static final float MUZZLE_FLASH_DURATION = 0.08f;
    private ShapeRenderer debugRenderer;
    private final Vector3 barrelTipWorld = new Vector3();
    private float muzzleFlashTimer;
    private Texture particleTexture;
    private static final Vector3 SUN_COLOR = new Vector3(1f, 0.95f, 0.9f);
    private static final Vector3 AMBIENT_COLOR = new Vector3(0.1f, 0.1f, 0.15f);

    private DialogHudSystem dialogHudSystem;
    private boolean inDialog;
    private InputAdapter dialogInputAdapter;
    private HackingOverlay hackingOverlay;
    private InventoryScreenSystem inventoryScreenSystem;
    private OutfitterScreenSystem outfitterScreenSystem;
    private QuestJournalOverlay questJournalOverlay;
    private com.galacticodyssey.ui.CharacterScreen characterScreen;
    private com.galacticodyssey.ui.LevelUpToastOverlay levelUpToast;
    private ScreenTabManager screenTabManager;
    private com.galacticodyssey.ui.systems.RecruitmentScreenSystem recruitmentScreen;
    private VehicleBayPanel vehicleBayPanel;
    private com.badlogic.gdx.scenes.scene2d.Stage vehicleBayStage;
    private BoardingResolutionPanel boardingResolutionPanel;
    private com.badlogic.gdx.scenes.scene2d.Stage boardingResolutionStage;

    // DEV-ONLY: F6 debug creature spawn (Task 14, Creature Generation Core)
    private FaunaDebugSpawner faunaDebugSpawner;
    private final Array<ModelInstance> creatureRenderQueue = new Array<>();

    // Preserve existing constructor for load-game flow
    public GameScreen(GalacticOdyssey game) {
        this(game, null);
    }

    // New constructor for new-game flow
    public GameScreen(GalacticOdyssey game, GameSession session) {
        this.game = game;
        this.session = session;
    }

    @Override
    public void show() {
        if (!initialized) {
            initializeWorld();
            initialized = true;
        }
        setupInput();
        if (paused) {
            Gdx.input.setCursorCatched(false);
        } else {
            Gdx.input.setCursorCatched(true);
        }
    }

    private void initializeWorld() {
        camera = new PerspectiveCamera(75, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 5000f;

        deferredRenderer = new DeferredRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        EventBus eventBus = new EventBus();
        CoordinateManager coordinateManager = new CoordinateManager(eventBus);
        gameWorld = new GameWorld(eventBus, coordinateManager);

        long terrainSeed = (session != null) ? session.terrainSeed : TERRAIN_SEED;

        heightmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, terrainSeed);

        gameWorld.initializeSystems(camera);

        Pixmap pix = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        particleTexture = new Texture(pix);
        pix.dispose();
        gameWorld.getParticleRenderSystem().initialize(new TextureRegion(particleTexture));

        populatedWorld = WorldPopulator.populate(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, terrainSeed);

        GrassRegistry grassRegistry = new GrassRegistry();
        grassRegistry.load("data/flora/grass.json");
        HeightmapTerrainSampler grassSampler = new HeightmapTerrainSampler(
            heightmap, populatedWorld.biomeGrid, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
        grassField = new GrassField(grassRegistry.config(), grassSampler, terrainSeed);
        grassRenderer = new GrassRenderer(deferredRenderer.getShaderCache(), grassRegistry.config(), GRASS_MAX_INSTANCES);

        AlienPlantRegistry alienRegistry = new AlienPlantRegistry();
        alienRegistry.load("data/flora/alien_plants.json");
        AlienPlantGenerator.populate(populatedWorld, alienRegistry, heightmap,
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, populatedWorld.seaLevel, terrainSeed);
        alienBatch = new ModelBatch(new EmissiveGBufferShaderProvider(deferredRenderer.getShaderCache()));

        gameWorld.getOceanSpawner().spawnSeawater(populatedWorld.seaLevel, terrainSeed);

        createTerrainMesh();
        createTerrainPhysics();
        createScatterBoxes();

        if (session != null && session.playerSpawnPos != null) {
            gameWorld.createPlayerEntity(
                session.playerSpawnPos.x, session.playerSpawnPos.y, session.playerSpawnPos.z);
        } else {
            float spawnHeight = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
            gameWorld.createPlayerEntity(0, spawnHeight, 0);
        }

        // Spawn test NPC near player for dialog testing (negative Z = in front of default camera)
        float npcX = 0f, npcZ = -2f;
        float npcY = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, npcX, npcZ) + 1f;
        gameWorld.spawnTestNpc(npcX, npcY, npcZ);

        shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());
        shipFactory.setReactorSpecRegistry(gameWorld.getReactorSpecRegistry());

        if (session != null && session.shipSpawnPos != null) {
            float shipY = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH,
                session.shipSpawnPos.x, session.shipSpawnPos.z) + 0.5f;
            Entity starterShip = shipFactory.createShip(
                session.seed, ShipSizeClass.SMALL,
                session.shipSpawnPos.x, shipY, session.shipSpawnPos.z);
            shipEntities.add(starterShip);
        } else {
            float smallX = 5f, smallZ = 5f;
            float smallY = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, smallX, smallZ) + 2f;
            Entity smallShip = shipFactory.createShip(42L, ShipSizeClass.SMALL, smallX, smallY, smallZ);
            shipEntities.add(smallShip);

            float medX = 40f, medZ = 40f;
            float medY = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, medX, medZ) + 4f;
            Entity medShip = shipFactory.createShip(123L, ShipSizeClass.MEDIUM, medX, medY, medZ);
            shipEntities.add(medShip);

            float lgX = -60f, lgZ = -60f;
            float lgY = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, lgX, lgZ) + 6f;
            Entity largeShip = shipFactory.createShip(999L, ShipSizeClass.LARGE, lgX, lgY, lgZ);
            shipEntities.add(largeShip);
        }

        buildShipMeshes();

        modelBatch = new ModelBatch();
        gbufferBatch = new ModelBatch(new GBufferBatchShaderProvider(deferredRenderer.getShaderCache()));
        creatureBatch = new com.badlogic.gdx.graphics.g3d.ModelBatch(
            new com.galacticodyssey.fauna.skin.CreatureSkinShaderProvider());
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));
        deferredRenderer.setDeferredEnabled(true);

        buildFirstPersonWeaponModel();
        debugRenderer = new ShapeRenderer();
        gameWorld.getEventBus().subscribe(WeaponFiredEvent.class, e -> {
            muzzleFlashTimer = MUZZLE_FLASH_DURATION;
        });

        // Vehicle bay rendering hooks
        vehicleFallbackModel = new ModelBuilder().createBox(
            2f, 1f, 4f,
            new Material(ColorAttribute.createDiffuse(new Color(0.3f, 0.5f, 0.7f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        disposables.add(vehicleFallbackModel);
        gameWorld.getEventBus().subscribe(com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent.class, e -> {
            com.badlogic.gdx.graphics.g3d.ModelInstance mi =
                new com.badlogic.gdx.graphics.g3d.ModelInstance(vehicleFallbackModel);
            vehicleInstances.put(e.vehicle, mi);
        });
        gameWorld.getEventBus().subscribe(com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent.class, e -> {
            vehicleInstances.entrySet().removeIf(entry -> entry.getKey().getComponents().size() == 0);
        });
        buildPauseMenu();
        buildDialogSystem();
        buildHackingSystem();
        buildScreenTabManager();

        atmosphericSkyRenderer = new AtmosphericSkyRenderer();
        dayNightCycle = new DayNightCycle(600f, 23.5f, false);

        Entity sunEntity = new Entity();
        LightComponent sunLight = new LightComponent();
        sunLight.type = LightComponent.Type.DIRECTIONAL;
        sunLight.color.set(1f, 0.95f, 0.9f, 1f);
        sunLight.intensity = 3f;
        sunEntity.add(sunLight);
        gameWorld.getEngine().addEntity(sunEntity);

        // DEV-ONLY: build the F6 debug creature spawner. Default fauna parts are all procedural,
        // so the AuthoredPartProvider's AssetManager is never touched for this content.
        FaunaDataRegistry faunaRegistry = new FaunaDataRegistry();
        faunaRegistry.loadParts("data/fauna/parts/default-parts.json");
        faunaRegistry.loadArchetypes("data/fauna/archetypes/default-archetypes.json");
        faunaRegistry.validate();
        CreatureGenerator creatureGenerator = new CreatureGenerator(faunaRegistry);
        CreatureMeshBuilder creatureMeshBuilder = new CreatureMeshBuilder(
            new ProceduralPartProvider(),
            new AuthoredPartProvider(null));
        disposables.add(creatureMeshBuilder);
        faunaDebugSpawner = new FaunaDebugSpawner(creatureGenerator, creatureMeshBuilder);
    }

    private void openGalaxyMap() {
        if (session == null || session.galaxy == null) return;
        Gdx.input.setCursorCatched(false);
        game.setScreen(new GalaxyMapScreen(game, session, this));
    }

    private boolean isAnyScreenOpen() {
        return screenTabManager != null && screenTabManager.isAnyOpen();
    }

    private void setupInput() {
        InputAdapter keyHandler = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (isAnyScreenOpen()) {
                        screenTabManager.closeActive();
                        return true;
                    }
                    togglePause();
                    return true;
                }
                if (keycode == Input.Keys.M) {
                    openGalaxyMap();
                    return true;
                }
                if (!paused && !inDialog) {
                    if (keycode == Input.Keys.TAB) {
                        if (isAnyScreenOpen() && "inventory".equals(screenTabManager.getActiveScreenName())) {
                            screenTabManager.closeActive();
                        } else {
                            screenTabManager.switchTo("inventory");
                        }
                        return true;
                    }
                    if (keycode == Input.Keys.O) {
                        Entity playerShip = shipEntities.size > 0 ? shipEntities.first() : null;
                        if (playerShip != null) {
                            outfitterScreenSystem.open(playerShip, true);
                            screenTabManager.switchTo("outfitter");
                        }
                        return true;
                    }
                    if (keycode == Input.Keys.J) {
                        if (isAnyScreenOpen() && "journal".equals(screenTabManager.getActiveScreenName())) {
                            screenTabManager.closeActive();
                        } else {
                            screenTabManager.switchTo("journal");
                        }
                        return true;
                    }
                    if (keycode == Input.Keys.C) {
                        if (isAnyScreenOpen() && "character".equals(screenTabManager.getActiveScreenName())) {
                            screenTabManager.closeActive();
                        } else {
                            screenTabManager.switchTo("character");
                        }
                        return true;
                    }
                    if (keycode == Input.Keys.B) {
                        if (vehicleBayPanel != null) {
                            if (vehicleBayPanel.isVisible()) {
                                vehicleBayPanel.hide();
                            } else {
                                vehicleBayPanel.show();
                            }
                        }
                        return true;
                    }
                }
                if (keycode == Input.Keys.F5) {
                    if (deferredRenderer != null) deferredRenderer.reloadShaders();
                    return true;
                }
                if (keycode == Input.Keys.F6) {
                    if (faunaDebugSpawner != null) {
                        var players = gameWorld.getEngine().getEntitiesFor(
                            Family.all(PlayerTagComponent.class, TransformComponent.class).get());
                        Vector3 origin = players.size() > 0
                            ? players.first().getComponent(TransformComponent.class).position
                            : camera.position;
                        faunaDebugSpawner.spawnInFront(
                            gameWorld.getEngine(), origin, camera.direction, 6f);
                    }
                    return true;
                }
                return false;
            }
        };

        inputMultiplexer.clear();
        if (boardingResolutionStage != null) inputMultiplexer.addProcessor(boardingResolutionStage);
        if (vehicleBayStage != null) inputMultiplexer.addProcessor(vehicleBayStage);
        inputMultiplexer.addProcessor(keyHandler);
        if (paused) {
            inputMultiplexer.addProcessor(pauseStage);
        } else if (inDialog) {
            inputMultiplexer.addProcessor(dialogInputAdapter);
        } else if (isAnyScreenOpen()) {
            inputMultiplexer.addProcessor(screenTabManager.getTabBarStage());
            ManagedScreen active = screenTabManager.getActiveScreen();
            if (active != null && active.getStage() != null) {
                inputMultiplexer.addProcessor(active.getStage());
            }
        } else {
            inputMultiplexer.addProcessor(gameWorld.getPlayerInputSystem().getInputAdapter());
        }
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            Gdx.input.setCursorCatched(false);
            gameWorld.getPlayerInputSystem().setEnabled(false);
            inputMultiplexer.clear();
            if (boardingResolutionStage != null) inputMultiplexer.addProcessor(boardingResolutionStage);
            if (vehicleBayStage != null) inputMultiplexer.addProcessor(vehicleBayStage);
            inputMultiplexer.addProcessor(new InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == Input.Keys.ESCAPE) {
                        togglePause();
                        return true;
                    }
                    return false;
                }
            });
            inputMultiplexer.addProcessor(pauseStage);
        } else {
            Gdx.input.setCursorCatched(true);
            gameWorld.getPlayerInputSystem().setEnabled(true);
            setupInput();
        }
    }

    private void buildFirstPersonWeaponModel() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        Material bodyMat = new Material(ColorAttribute.createDiffuse(new Color(0.25f, 0.25f, 0.28f, 1f)));
        Material barrelMat = new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.18f, 1f)));
        Material gripMat = new Material(ColorAttribute.createDiffuse(new Color(0.12f, 0.10f, 0.08f, 1f)));
        Material magMat = new Material(ColorAttribute.createDiffuse(new Color(0.20f, 0.20f, 0.22f, 1f)));
        Material sightMat = new Material(ColorAttribute.createDiffuse(new Color(0.10f, 0.10f, 0.12f, 1f)));
        Material accentMat = new Material(ColorAttribute.createDiffuse(new Color(0.0f, 0.6f, 0.8f, 1f)));
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        // Receiver / main body
        mb.part("body", GL20.GL_TRIANGLES, attrs, bodyMat)
            .box(0f, 0f, -0.12f, 0.045f, 0.055f, 0.28f);
        // Barrel
        mb.part("barrel", GL20.GL_TRIANGLES, attrs, barrelMat)
            .box(0f, 0.005f, -0.36f, 0.025f, 0.025f, 0.22f);
        // Barrel shroud / handguard
        mb.part("handguard", GL20.GL_TRIANGLES, attrs, bodyMat)
            .box(0f, 0.002f, -0.30f, 0.035f, 0.038f, 0.14f);
        // Pistol grip
        mb.part("grip", GL20.GL_TRIANGLES, attrs, gripMat)
            .box(0.0f, -0.055f, -0.05f, 0.028f, 0.065f, 0.035f);
        // Magazine
        mb.part("magazine", GL20.GL_TRIANGLES, attrs, magMat)
            .box(0.0f, -0.06f, -0.14f, 0.022f, 0.07f, 0.03f);
        // Stock
        mb.part("stock", GL20.GL_TRIANGLES, attrs, bodyMat)
            .box(0f, -0.005f, 0.08f, 0.03f, 0.045f, 0.12f);
        // Stock buttpad
        mb.part("buttpad", GL20.GL_TRIANGLES, attrs, gripMat)
            .box(0f, -0.005f, 0.145f, 0.032f, 0.048f, 0.015f);
        // Front sight post
        mb.part("front_sight", GL20.GL_TRIANGLES, attrs, sightMat)
            .box(0f, 0.04f, -0.35f, 0.005f, 0.018f, 0.005f);
        // Rear sight
        mb.part("rear_sight", GL20.GL_TRIANGLES, attrs, sightMat)
            .box(0f, 0.04f, -0.06f, 0.018f, 0.015f, 0.008f);
        // Accent strip on receiver
        mb.part("accent", GL20.GL_TRIANGLES, attrs, accentMat)
            .box(0f, 0.03f, -0.12f, 0.046f, 0.003f, 0.10f);
        // Muzzle device
        mb.part("muzzle", GL20.GL_TRIANGLES, attrs, barrelMat)
            .box(0f, 0.005f, -0.475f, 0.018f, 0.018f, 0.02f);

        fpWeaponModel = mb.end();
        fpWeaponInstance = new ModelInstance(fpWeaponModel);
    }

    private void renderFirstPersonWeapon(float delta) {
        if (fpWeaponInstance == null || gameWorld == null) return;

        var players = gameWorld.getEngine().getEntitiesFor(
            Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
        if (players.size() == 0) return;
        Entity player = players.first();

        PlayerStateComponent pState = player.getComponent(PlayerStateComponent.class);
        if (pState != null && pState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) return;

        WeaponInventoryComponent inv = player.getComponent(WeaponInventoryComponent.class);
        if (inv != null && inv.isActiveSlotMelee()) return;

        FPSCameraComponent cam = player.getComponent(FPSCameraComponent.class);
        if (cam != null && cam.currentCameraDistance >= 0.1f) return;

        MovementStateComponent movement = player.getComponent(MovementStateComponent.class);

        // Weapon bob
        if (movement != null && movement.currentSpeed > 0.5f && movement.isGrounded) {
            weaponBobTimer += delta * 6f;
        } else {
            weaponBobTimer *= 0.95f;
        }
        float bobX = MathUtils.sin(weaponBobTimer) * 0.003f;
        float bobY = MathUtils.sin(weaponBobTimer * 2f) * 0.002f;

        // Weapon sway from mouse movement
        if (cam != null) {
            float targetSwayX = -cam.pitchAngle * 0.0002f;
            float targetSwayY = 0f;
            weaponSwayX = MathUtils.lerp(weaponSwayX, targetSwayX, 5f * delta);
            weaponSwayY = MathUtils.lerp(weaponSwayY, targetSwayY, 5f * delta);
        }

        // Position weapon relative to camera
        Matrix4 camInv = new Matrix4(camera.view).inv();
        fpWeaponInstance.transform.set(camInv);
        fpWeaponInstance.transform.translate(
            0.18f + bobX + weaponSwayY,
            -0.14f + bobY + weaponSwayX,
            -0.25f
        );

        // Apply lean roll to weapon
        if (cam != null && Math.abs(cam.leanAngle) > 0.01f) {
            fpWeaponInstance.transform.rotate(0, 0, 1, cam.leanAngle);
        }

        // Scale the weapon down slightly
        fpWeaponInstance.transform.scl(0.7f);

        // Compute world-space barrel tip by transforming model-local muzzle point through the
        // final weapon transform — this handles bob, sway, and lean correctly.
        Vector3 muzzleTip = new Vector3(0f, 0.005f, -0.475f).mul(fpWeaponInstance.transform);
        barrelTipWorld.set(muzzleTip);
        if (cam != null) cam.worldBarrelTip.set(muzzleTip);

        // Render with cleared depth and tight near plane
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        float savedNear = camera.near;
        camera.near = 0.01f;
        camera.update();

        gbufferBatch.begin(camera);
        gbufferBatch.render(fpWeaponInstance);
        gbufferBatch.end();

        camera.near = savedNear;
        camera.update();
    }

    /** Screen-space muzzle flash drawn after the FP weapon so it always appears in front of the gun. */
    private void renderMuzzleFlash(float delta) {
        if (debugRenderer == null || muzzleFlashTimer <= 0) return;
        muzzleFlashTimer = Math.max(0, muzzleFlashTimer - delta);
        float t = muzzleFlashTimer / MUZZLE_FLASH_DURATION;

        Vector3 screenPos = camera.project(new Vector3(barrelTipWorld));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive
        debugRenderer.setProjectionMatrix(
            new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        debugRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Outer glow expands and fades
        debugRenderer.setColor(1f, 0.7f, 0.2f, t * 0.45f);
        debugRenderer.circle(screenPos.x, screenPos.y, 40f + 20f * (1f - t));
        // Inner flash stays compact and bright
        debugRenderer.setColor(1f, 0.95f, 0.7f, t * 0.85f);
        debugRenderer.circle(screenPos.x, screenPos.y, 18f);
        debugRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void buildPauseMenu() {
        pauseStage = new Stage(new FitViewport(PAUSE_WORLD_WIDTH, PAUSE_WORLD_HEIGHT));

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0f, 0f, 0.7f));
        pixmap.fill();
        overlayTexture = new Texture(pixmap);
        pixmap.dispose();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        root.center();

        Skin skin = game.getSkin();
        AudioManager audio = game.getAudioManager();

        Label title = new Label("PAUSED", skin, "title");
        root.add(title).padBottom(40).row();

        addPauseButton(root, "Resume", skin, audio, this::togglePause);
        addPauseButton(root, "Galaxy Map", skin, audio, () -> {
            togglePause();
            openGalaxyMap();
        });
        addPauseButton(root, "Save Game", skin, audio, () -> {
            game.setScreen(new SaveScreen(game, game.getSaveBackend(), GameScreen.this));
        });
        addPauseButton(root, "Load Game", skin, audio, () -> {
            game.setScreen(new LoadScreen(game, game.getSaveBackend(),
                GameScreen.this, LoadScreen.Origin.PAUSE_MENU, GameScreen.this));
        });
        addPauseButton(root, "Settings", skin, audio, () -> {
            game.setScreen(new SettingsScreen(game, this));
        });
        addPauseButton(root, "Exit to Main Menu", skin, audio, () -> {
            dispose();
            game.setScreen(new MainMenuScreen(game));
        });
        addPauseButton(root, "Exit Game", skin, audio, () -> Gdx.app.exit());

        pauseStage.addActor(root);
    }

    private void addPauseButton(Table table, String text, Skin skin, AudioManager audio, Runnable action) {
        TextButton button = new TextButton(text, skin);
        button.setTransform(true);

        button.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    button.setOrigin(Align.center);
                    button.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audio.playSound("audio/sfx/ui_hover.ogg");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    button.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                action.run();
            }
        });

        table.add(button).width(300).height(50).padBottom(12).row();
    }

    private void buildHackingSystem() {
        EventBus eventBus = gameWorld.getEventBus();
        hackingOverlay = new HackingOverlay(eventBus, game.getSkin());

        eventBus.subscribe(HackStartedEvent.class, event -> {
            com.badlogic.ashley.core.Entity player = event.player;
            HackingStateComponent hackState = player.getComponent(HackingStateComponent.class);
            if (hackState != null && hackState.controller != null) {
                hackingOverlay.show(hackState.controller);
            }
        });
        eventBus.subscribe(HackSucceededEvent.class, e -> {
            hackingOverlay.hide();
            Gdx.input.setInputProcessor(inputMultiplexer);
        });
        eventBus.subscribe(HackFailedEvent.class, e -> {
            hackingOverlay.hide();
            Gdx.input.setInputProcessor(inputMultiplexer);
        });
    }

    private void buildScreenTabManager() {
        EventBus eventBus = gameWorld.getEventBus();
        Skin skin = game.getSkin();

        screenTabManager = new ScreenTabManager(skin);

        inventoryScreenSystem = new InventoryScreenSystem(eventBus, skin);
        inventoryScreenSystem.initialize(gameWorld.getEngine(), gameWorld.getEquipmentSystem());
        screenTabManager.register("inventory", inventoryScreenSystem);

        eventBus.subscribe(com.galacticodyssey.ui.events.InventoryOpenedEvent.class, event -> {
            inventoryScreenSystem.refreshAll();
        });

        ShipModuleRegistry moduleRegistry = new ShipModuleRegistry();
        moduleRegistry.loadModules("data/modules/ship_modules.json");
        moduleRegistry.loadSlotLayouts("data/modules/ship_module_slots.json");

        outfitterScreenSystem = new OutfitterScreenSystem(eventBus, skin, moduleRegistry);
        outfitterScreenSystem.initialize(gameWorld.getEngine());
        shipFactory.setModuleRegistry(moduleRegistry);
        screenTabManager.register("outfitter", outfitterScreenSystem);

        questJournalOverlay = new QuestJournalOverlay(eventBus, skin);
        QuestJournal journal = gameWorld.getQuestJournal();
        JobRegistry jobRegistry = gameWorld.getJobRegistry();
        SagaRegistry sagaRegistry = gameWorld.getSagaRegistry();
        ReputationQuery reputation = tag -> 0f;
        questJournalOverlay.initialize(journal, null, jobRegistry, sagaRegistry, reputation);
        screenTabManager.register("journal", questJournalOverlay);

        recruitmentScreen = new com.galacticodyssey.ui.systems.RecruitmentScreenSystem(eventBus, skin);
        recruitmentScreen.initialize(gameWorld.getEngine());
        screenTabManager.register("recruitment", recruitmentScreen);

        characterScreen = new com.galacticodyssey.ui.CharacterScreen(eventBus, skin);
        characterScreen.initialize(gameWorld.getEngine(),
            gameWorld.getRealTimeSkillSystem(), gameWorld.getPerkSystem(), gameWorld.getPerkRegistry());
        screenTabManager.register("character", characterScreen);

        levelUpToast = new com.galacticodyssey.ui.LevelUpToastOverlay(eventBus, skin);

        screenTabManager.setTransitionListener(new ScreenTabManager.ScreenTransitionListener() {
            @Override
            public void onScreenOpened(String name, ManagedScreen screen) {
                Gdx.input.setCursorCatched(false);
                gameWorld.getPlayerInputSystem().setEnabled(false);
                setupInput();
            }

            @Override
            public void onAllScreensClosed() {
                Gdx.input.setCursorCatched(true);
                gameWorld.getPlayerInputSystem().setEnabled(true);
                setupInput();
            }
        });

        // Vehicle bay overlay (standalone panel — not a ManagedScreen)
        vehicleBayStage = new com.badlogic.gdx.scenes.scene2d.Stage(
            new com.badlogic.gdx.utils.viewport.ScreenViewport());
        java.util.function.Supplier<com.badlogic.ashley.core.Entity> bayShipSupplier = () -> {
            com.badlogic.ashley.utils.ImmutableArray<com.badlogic.ashley.core.Entity> ships =
                gameWorld.getEngine().getEntitiesFor(
                    com.badlogic.ashley.core.Family.all(VehicleBayComponent.class).get());
            return ships.size() > 0 ? ships.first() : null;
        };
        vehicleBayPanel = new VehicleBayPanel(
            game.getSkin(),
            gameWorld.getVehicleRegistry(),
            gameWorld.getVehicleBayService(),
            bayShipSupplier,
            gameWorld.getEventBus());
        vehicleBayPanel.setFillParent(true);
        vehicleBayStage.addActor(vehicleBayPanel);

        // Boarding resolution overlay (standalone panel — shown via BoardingResolutionRequestedEvent)
        boardingResolutionStage = new com.badlogic.gdx.scenes.scene2d.Stage(
            new com.badlogic.gdx.utils.viewport.ScreenViewport());
        boardingResolutionPanel = new BoardingResolutionPanel(game.getSkin(), gameWorld.getEventBus());
        boardingResolutionPanel.setFillParent(true);
        boardingResolutionStage.addActor(boardingResolutionPanel);
    }
    private void buildDialogSystem() {
        EventBus eventBus = gameWorld.getEventBus();
        DialogSystem dialogSystem = gameWorld.getDialogSystem();

        dialogHudSystem = new DialogHudSystem(eventBus, game.getSkin());
        dialogHudSystem.initialize();

        dialogInputAdapter = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_4) {
                    dialogSystem.selectChoice(keycode - Input.Keys.NUM_1);
                    return true;
                }
                if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
                    dialogSystem.advanceOrClose();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    dialogSystem.closeDialog();
                    return true;
                }
                return true;
            }

            @Override
            public boolean keyUp(int keycode) { return true; }
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) { return true; }
            @Override
            public boolean touchUp(int x, int y, int pointer, int button) { return true; }
            @Override
            public boolean mouseMoved(int x, int y) { return true; }
            @Override
            public boolean scrolled(float amountX, float amountY) { return true; }
        };

        eventBus.subscribe(DialogOpenedEvent.class, event -> {
            inDialog = true;
            Gdx.input.setCursorCatched(false);
            gameWorld.getPlayerInputSystem().setEnabled(false);
            inputMultiplexer.clear();
            inputMultiplexer.addProcessor(dialogInputAdapter);
        });

        eventBus.subscribe(DialogClosedEvent.class, event -> {
            inDialog = false;
            Gdx.input.setCursorCatched(true);
            gameWorld.getPlayerInputSystem().setEnabled(true);
            setupInput();
        });
    }

    private void createTerrainMesh() {
        float[] normals = TerrainGenerator.computeNormals(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);

        int vertCount = TERRAIN_VERTS_X * TERRAIN_VERTS_Z;
        float[] vertices = new float[vertCount * 10];

        float cellW = TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1);
        float cellD = TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1);
        float halfW = TERRAIN_WIDTH / 2f;
        float halfD = TERRAIN_DEPTH / 2f;

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float h : heightmap) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        for (int z = 0; z < TERRAIN_VERTS_Z; z++) {
            for (int x = 0; x < TERRAIN_VERTS_X; x++) {
                int idx = z * TERRAIN_VERTS_X + x;
                int vi = idx * 10;
                float h = heightmap[idx];

                float wx = x * cellW - halfW;
                float wz = z * cellD - halfD;

                vertices[vi]     = wx;
                vertices[vi + 1] = h;
                vertices[vi + 2] = wz;

                vertices[vi + 3] = normals[idx * 3];
                vertices[vi + 4] = normals[idx * 3 + 1];
                vertices[vi + 5] = normals[idx * 3 + 2];

                float slope = 1f - normals[idx * 3 + 1];
                float heightFrac = (h - minH) / (maxH - minH + 0.001f);

                BiomeType biome = populatedWorld.biomeGrid[idx];
                Color biomeCol = WorldPopulator.biomeColor(biome, heightFrac, slope,
                    wx, wz, h, minH, maxH, populatedWorld.noisePerm,
                    populatedWorld.biomeGrid, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, x, z);

                vertices[vi + 6] = biomeCol.r;
                vertices[vi + 7] = biomeCol.g;
                vertices[vi + 8] = biomeCol.b;
                vertices[vi + 9] = 1f;
            }
        }

        int quadCount = (TERRAIN_VERTS_X - 1) * (TERRAIN_VERTS_Z - 1);
        short[] indices = new short[quadCount * 6];
        int ii = 0;
        for (int z = 0; z < TERRAIN_VERTS_Z - 1; z++) {
            for (int x = 0; x < TERRAIN_VERTS_X - 1; x++) {
                short topLeft = (short) (z * TERRAIN_VERTS_X + x);
                short topRight = (short) (topLeft + 1);
                short botLeft = (short) ((z + 1) * TERRAIN_VERTS_X + x);
                short botRight = (short) (botLeft + 1);

                indices[ii++] = topLeft;
                indices[ii++] = botLeft;
                indices[ii++] = topRight;
                indices[ii++] = topRight;
                indices[ii++] = botLeft;
                indices[ii++] = botRight;
            }
        }

        terrainMesh = new Mesh(true, vertCount, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));

        terrainMesh.setVertices(vertices);
        terrainMesh.setIndices(indices);
    }

    private void createTerrainPhysics() {
        float cellW = TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1);
        float cellD = TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1);
        float halfW = TERRAIN_WIDTH / 2f;
        float halfD = TERRAIN_DEPTH / 2f;

        btTriangleMesh triMesh = new btTriangleMesh();
        disposables.add(triMesh);

        Vector3 v0 = new Vector3(), v1 = new Vector3();
        Vector3 v2 = new Vector3(), v3 = new Vector3();

        for (int z = 0; z < TERRAIN_VERTS_Z - 1; z++) {
            for (int x = 0; x < TERRAIN_VERTS_X - 1; x++) {
                float x0 = x * cellW - halfW;
                float x1 = (x + 1) * cellW - halfW;
                float z0 = z * cellD - halfD;
                float z1 = (z + 1) * cellD - halfD;

                float h00 = heightmap[z * TERRAIN_VERTS_X + x];
                float h10 = heightmap[z * TERRAIN_VERTS_X + x + 1];
                float h01 = heightmap[(z + 1) * TERRAIN_VERTS_X + x];
                float h11 = heightmap[(z + 1) * TERRAIN_VERTS_X + x + 1];

                v0.set(x0, h00, z0);
                v1.set(x0, h01, z1);
                v2.set(x1, h10, z0);
                v3.set(x1, h11, z1);

                triMesh.addTriangle(v0, v1, v2);
                triMesh.addTriangle(v2, v1, v3);
            }
        }

        btBvhTriangleMeshShape terrainShape = new btBvhTriangleMeshShape(triMesh, true);
        disposables.add(terrainShape);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrainBody = new btRigidBody(info);
        terrainBody.setFriction(0.9f);
        info.dispose();

        gameWorld.addTerrainBody(terrainBody);
    }

    private void createScatterBoxes() {
        Random rng = new Random(123L);
        ModelBuilder modelBuilder = new ModelBuilder();
        int attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        for (int i = 0; i < 15; i++) {
            float halfExt = 0.5f + rng.nextFloat() * 1.0f;
            float bx = (rng.nextFloat() - 0.5f) * TERRAIN_WIDTH * 0.6f;
            float bz = (rng.nextFloat() - 0.5f) * TERRAIN_DEPTH * 0.6f;
            float by = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, bx, bz)
                + halfExt + 1f;
            float mass = 50f + rng.nextFloat() * 150f;

            Entity boxEntity = gameWorld.createDynamicBox(bx, by, bz, halfExt, mass);
            boxEntities.add(boxEntity);

            float r = 0.3f + rng.nextFloat() * 0.7f;
            float g = 0.3f + rng.nextFloat() * 0.7f;
            float b = 0.3f + rng.nextFloat() * 0.7f;

            Model boxModel = modelBuilder.createBox(
                halfExt * 2, halfExt * 2, halfExt * 2,
                new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f))), attrs);
            disposables.add(boxModel);

            ModelInstance instance = new ModelInstance(boxModel);
            instance.transform.setToTranslation(bx, by, bz);
            boxInstances.add(instance);
        }
    }

    private void buildShipMeshes() {
        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            ShipDataComponent data = ship.getComponent(ShipDataComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            HullGeometry hull = data.hullGeometry;

            Mesh mesh = new Mesh(true, hull.vertexCount(), hull.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_emissive"));

            mesh.setVertices(hull.vertices);
            mesh.setIndices(hull.indices);
            meshComp.hullMesh = mesh;
        }
    }

    private ShaderProgram getShipShader() {
        if (shipShader != null) return shipShader;

        String vert =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "attribute float a_emissive;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "uniform mat4 u_worldTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "varying vec3 v_worldPos;\n" +
            "void main() {\n" +
            "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
            "    v_worldPos = worldPos.xyz;\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    v_emissive = a_emissive;\n" +
            "    gl_Position = u_projViewTrans * worldPos;\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "varying vec3 v_worldPos;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "uniform vec3 u_cameraPos;\n" +
            "uniform float u_fogDensity;\n" +
            "uniform vec3 u_fogColor;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    vec3 baseColor = mix(lit, v_color.rgb * 2.0, v_emissive);\n" +
            "    float dist = length(v_worldPos - u_cameraPos);\n" +
            "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
            "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
            "    vec3 color = mix(u_fogColor, baseColor, fogFactor);\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        shipShader = new ShaderProgram(vert, frag);
        if (!shipShader.isCompiled()) {
            Gdx.app.error("ShipShader", shipShader.getLog());
        }
        return shipShader;
    }

    private Entity getPilotedShip() {
        var players = gameWorld.getEngine().getEntitiesFor(
            Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
        if (players.size() == 0) return null;
        PlayerStateComponent state = players.first().getComponent(PlayerStateComponent.class);
        if (state.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return state.currentShip;
        }
        return null;
    }

    private void renderShips() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        Entity pilotedShip = getPilotedShip();

        ShaderProgram shader = deferredRenderer.getShaderCache()
            .get("gbuffer.vert", "gbuffer.frag", "HAS_VERTEX_COLOR", "HAS_EMISSIVE_ATTRIB");
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
        shader.setUniformf("u_metallicScale", 0.3f);
        shader.setUniformf("u_roughnessScale", 0.5f);
        shader.setUniformf("u_emissiveIntensity", 3f);
        shader.setUniformf("u_tiling", 1f, 1f);

        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            if (ship == pilotedShip) continue;

            TransformComponent t = ship.getComponent(TransformComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            if (meshComp == null || meshComp.hullMesh == null) continue;

            tmpMat4.set(t.position, t.rotation);
            shader.setUniformMatrix("u_worldTrans", tmpMat4);

            tmpViewModelMat4.set(camera.view).mul(tmpMat4);
            tmpNormalMat3.set(tmpViewModelMat4).inv().transpose();
            shader.setUniformMatrix("u_normalMatrix", tmpNormalMat3);

            meshComp.hullMesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private ShaderProgram getTerrainShader() {
        if (terrainShader != null) return terrainShader;

        String vert =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "uniform mat4 u_worldTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_worldPos;\n" +
            "void main() {\n" +
            "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
            "    v_worldPos = worldPos.xyz;\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = u_projViewTrans * worldPos;\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_worldPos;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "uniform vec3 u_cameraPos;\n" +
            "uniform float u_fogDensity;\n" +
            "uniform vec3 u_fogColor;\n" +
            "void main() {\n" +
            "    vec3 n = normalize(v_normal);\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(n, lightDir), 0.0);\n" +
            "    vec3 color = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    float dist = length(v_worldPos - u_cameraPos);\n" +
            "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
            "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
            "    color = mix(u_fogColor, color, fogFactor);\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        terrainShader = new ShaderProgram(vert, frag);
        if (!terrainShader.isCompiled()) {
            Gdx.app.error("Shader", terrainShader.getLog());
        }
        return terrainShader;
    }

    @Override
    public void render(float delta) {
        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.update(clampedDelta);
            dayNightCycle.update(clampedDelta);
            gameTime += clampedDelta;
        }

        if (!paused) {
            WorldPopulator.updateAnimals(populatedWorld, delta,
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
        }

        syncBoxTransforms();

        atmosphericSkyRenderer.setSunDirection(dayNightCycle.getSunDirection());
        atmosphericSkyRenderer.setTime(gameTime);

        Vector3 sunDir = dayNightCycle.getSunDirection();
        float sunIntensity = dayNightCycle.getSunIntensity();
        float ambientIntensity = dayNightCycle.getAmbientIntensity();

        if (grassField != null && grassField.update(camera.position.x, camera.position.z)) {
            grassRenderer.setInstances(grassField.instanceBuffer(), grassField.instanceCount());
        }

        deferredRenderer.render(
            camera,
            () -> {
                renderTerrain();
                if (grassRenderer != null) grassRenderer.render(camera, gameTime);
                renderBoxes();
                renderWorldObjects();
                renderAlienPlants();
                renderShips();
                renderCreatures();
            },
            () -> renderFirstPersonWeapon(delta),
            atmosphericSkyRenderer,
            () -> {
                if (populatedWorld != null && populatedWorld.waterInstance != null) {
                    modelBatch.begin(camera);
                    modelBatch.render(populatedWorld.waterInstance, environment);
                    modelBatch.end();
                }
            },
            () -> gameWorld.getParticleRenderSystem().render(),
            gameWorld.getLightingSystem(),
            sunDir,
            SUN_COLOR,
            sunIntensity * 3f,
            AMBIENT_COLOR,
            ambientIntensity
        );

        // Screen-space effects (after deferred pipeline outputs to screen)
        renderMuzzleFlash(delta);

        // HUD / UI (renders to screen directly)
        gameWorld.getCockpitHUDSystem().render(delta);
        gameWorld.getDebugHudSystem().render(delta);
        if (dialogHudSystem != null) dialogHudSystem.render(delta);
        if (hackingOverlay != null) hackingOverlay.render(delta);
        if (levelUpToast != null) levelUpToast.render(delta);
        if (screenTabManager != null) screenTabManager.render(delta);
        if (vehicleBayPanel != null && vehicleBayPanel.isVisible()) {
            vehicleBayStage.act(delta);
            vehicleBayStage.draw();
        }
        if (boardingResolutionPanel != null && boardingResolutionPanel.isVisible()) {
            boardingResolutionStage.act(delta);
            boardingResolutionStage.draw();
        }
        if (paused) { pauseStage.act(delta); pauseStage.draw(); }
    }

    private void syncBoxTransforms() {
        for (int i = 0; i < boxEntities.size; i++) {
            Entity entity = boxEntities.get(i);
            TransformComponent t = entity.getComponent(TransformComponent.class);
            boxInstances.get(i).transform.setToTranslation(t.position);
            boxInstances.get(i).transform.rotate(t.rotation);
        }
        for (var entry : vehicleInstances.entrySet()) {
            TransformComponent t =
                entry.getKey().getComponent(TransformComponent.class);
            if (t != null) {
                entry.getValue().transform.set(t.position, t.rotation);
            }
        }
    }

    private void renderTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        ShaderProgram shader = deferredRenderer.getShaderCache()
            .get("gbuffer.vert", "gbuffer.frag", "HAS_VERTEX_COLOR");
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        tmpMat4.idt();
        shader.setUniformMatrix("u_worldTrans", tmpMat4);

        tmpMat4.set(camera.view);
        tmpNormalMat3.set(tmpMat4).inv().transpose();
        shader.setUniformMatrix("u_normalMatrix", tmpNormalMat3);

        shader.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
        shader.setUniformf("u_metallicScale", 0f);
        shader.setUniformf("u_roughnessScale", 0.85f);
        shader.setUniformf("u_emissiveIntensity", 0f);
        shader.setUniformf("u_tiling", 1f, 1f);

        terrainMesh.render(shader, GL20.GL_TRIANGLES);
    }

    private void renderBoxes() {
        gbufferBatch.begin(camera);
        for (int i = 0; i < boxInstances.size; i++) {
            gbufferBatch.render(boxInstances.get(i));
        }
        for (com.badlogic.gdx.graphics.g3d.ModelInstance mi : vehicleInstances.values()) {
            gbufferBatch.render(mi);
        }
        gbufferBatch.end();
    }

    private void renderWorldObjects() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        gbufferBatch.begin(camera);
        for (int i = 0; i < populatedWorld.treeInstances.size; i++) {
            gbufferBatch.render(populatedWorld.treeInstances.get(i));
        }
        for (int i = 0; i < populatedWorld.rockInstances.size; i++) {
            gbufferBatch.render(populatedWorld.rockInstances.get(i));
        }
        for (int i = 0; i < populatedWorld.animalInstances.size; i++) {
            gbufferBatch.render(populatedWorld.animalInstances.get(i));
        }
        gbufferBatch.end();
    }

    private void renderAlienPlants() {
        if (alienBatch == null || populatedWorld == null || populatedWorld.alienInstances.size == 0) return;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        alienBatch.begin(camera);
        for (int i = 0; i < populatedWorld.alienInstances.size; i++) {
            alienBatch.render(populatedWorld.alienInstances.get(i));
        }
        alienBatch.end();
    }

    /** DEV-ONLY: render debug-spawned creatures (F6). Instances live on CreatureRenderComponent. */
    @SuppressWarnings("unchecked")
    private void renderCreatures() {
        if (gameWorld == null) return;
        var creatures = gameWorld.getEngine().getEntitiesFor(
            Family.all(CreatureRenderComponent.class).get());
        if (creatures.size() == 0) return;

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        creatureRenderQueue.clear();
        for (int i = 0; i < creatures.size(); i++) {
            CreatureRenderComponent render = creatures.get(i).getComponent(CreatureRenderComponent.class);
            if (render.skinnedInstance != null) {
                creatureRenderQueue.add(render.skinnedInstance);
            } else if (render.modelInstance instanceof Array) {
                creatureRenderQueue.addAll((Array<ModelInstance>) render.modelInstance);
            }
        }
        if (creatureRenderQueue.size == 0) return;

        creatureBatch.begin(camera);
        for (int i = 0; i < creatureRenderQueue.size; i++) {
            creatureBatch.render(creatureRenderQueue.get(i));
        }
        creatureBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        if (deferredRenderer != null) deferredRenderer.resize(width, height);
        gameWorld.resize(width, height);
        pauseStage.getViewport().update(width, height, true);
        if (dialogHudSystem != null) dialogHudSystem.resize(width, height);
        if (hackingOverlay != null) hackingOverlay.resize(width, height);
        if (levelUpToast != null) levelUpToast.resize(width, height);
        if (screenTabManager != null) screenTabManager.resize(width, height);
        if (vehicleBayStage != null) vehicleBayStage.getViewport().update(width, height, true);
        if (boardingResolutionStage != null) boardingResolutionStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (deferredRenderer != null) { deferredRenderer.dispose(); deferredRenderer = null; }
        if (grassRenderer != null) { grassRenderer.dispose(); grassRenderer = null; }
        if (alienBatch != null) { alienBatch.dispose(); alienBatch = null; }
        // ShipFactory must dispose before gameWorld, because it removes rigid bodies
        // from the dynamics world that gameWorld owns.
        if (shipFactory != null) { shipFactory.dispose(); shipFactory = null; }
        if (gameWorld != null) {
            gameWorld.dispose();
            gameWorld = null;
        }
        if (terrainMesh != null) {
            terrainMesh.dispose();
            terrainMesh = null;
        }
        if (terrainShader != null) {
            terrainShader.dispose();
            terrainShader = null;
        }
        if (atmosphericSkyRenderer != null) {
            atmosphericSkyRenderer.dispose();
            atmosphericSkyRenderer = null;
        }
        if (fpWeaponModel != null) {
            fpWeaponModel.dispose();
            fpWeaponModel = null;
            fpWeaponInstance = null;
        }
        if (debugRenderer != null) {
            debugRenderer.dispose();
            debugRenderer = null;
        }
        if (particleTexture != null) {
            particleTexture.dispose();
            particleTexture = null;
        }
        if (modelBatch != null) {
            modelBatch.dispose();
            modelBatch = null;
        }
        if (gbufferBatch != null) {
            gbufferBatch.dispose();
            gbufferBatch = null;
        }
        if (creatureBatch != null) {
            creatureBatch.dispose();
            creatureBatch = null;
        }
        if (dialogHudSystem != null) {
            dialogHudSystem.dispose();
            dialogHudSystem = null;
        }
        if (hackingOverlay != null) {
            hackingOverlay.dispose();
            hackingOverlay = null;
        }
        if (inventoryScreenSystem != null) {
            inventoryScreenSystem.dispose();
            inventoryScreenSystem = null;
        }
        if (outfitterScreenSystem != null) {
            outfitterScreenSystem.dispose();
            outfitterScreenSystem = null;
        }
        if (recruitmentScreen != null) {
            recruitmentScreen.dispose();
            recruitmentScreen = null;
        }
        if (questJournalOverlay != null) {
            questJournalOverlay.dispose();
            questJournalOverlay = null;
        }
        if (characterScreen != null) {
            characterScreen.dispose();
            characterScreen = null;
        }
        if (levelUpToast != null) { levelUpToast.dispose(); levelUpToast = null; }
        if (vehicleBayPanel != null) {
            vehicleBayPanel.dispose();
            vehicleBayPanel = null;
        }
        if (vehicleBayStage != null) {
            vehicleBayStage.dispose();
            vehicleBayStage = null;
        }
        if (boardingResolutionPanel != null) { boardingResolutionPanel.dispose(); boardingResolutionPanel = null; }
        if (boardingResolutionStage != null) { boardingResolutionStage.dispose(); boardingResolutionStage = null; }
        if (screenTabManager != null) {
            screenTabManager.dispose();
            screenTabManager = null;
        }
        if (pauseStage != null) {
            pauseStage.dispose();
            pauseStage = null;
        }
        if (overlayTexture != null) {
            overlayTexture.dispose();
            overlayTexture = null;
        }
        if (populatedWorld != null) {
            populatedWorld.dispose();
            populatedWorld = null;
        }
        for (int i = 0; i < shipEntities.size; i++) {
            ShipMeshComponent meshComp = shipEntities.get(i).getComponent(ShipMeshComponent.class);
            if (meshComp != null) meshComp.dispose();
        }
        shipEntities.clear();
        if (shipShader != null) { shipShader.dispose(); shipShader = null; }
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        boxInstances.clear();
        boxEntities.clear();
        if (session != null && session.galaxy != null) {
            session.galaxy.dispose();
        }
        initialized = false;
    }
}
