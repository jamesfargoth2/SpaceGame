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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
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
import com.galacticodyssey.galaxy.LuminosityClass;
import com.galacticodyssey.galaxy.OrbitalSlot;
import com.galacticodyssey.galaxy.OrbitalZone;
import com.galacticodyssey.galaxy.SpectralClass;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.AtmosphereGenerator;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeMapper;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import com.galacticodyssey.planet.terrain.CubeFace;
import com.galacticodyssey.planet.terrain.CubeSphere;
import com.galacticodyssey.planet.terrain.TerrainChunk;
import com.galacticodyssey.planet.terrain.TerrainNoiseStack;
import com.galacticodyssey.data.WorldPopulator;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipFactory;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipMeshComponent;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;

import java.util.List;

public class GameScreen implements Screen {

    private static final long PLANET_SEED = 42L;
    private int frameCount;
    private static final float PAUSE_WORLD_WIDTH = 1280f;
    private static final float PAUSE_WORLD_HEIGHT = 720f;
    private static final float ATMOSPHERE_ALTITUDE = 100f;

    private final GalacticOdyssey game;
    private GameWorld gameWorld;
    private PerspectiveCamera camera;
    private float planetRadius;

    private ShipFactory shipFactory;
    private final Array<Entity> shipEntities = new Array<>();
    private ShaderProgram shipShader;

    private ShaderProgram terrainShader;
    private ModelBatch modelBatch;
    private Environment environment;
    private final Array<Disposable> disposables = new Array<>();

    private WorldPopulator.PopulatedWorld populatedWorld;

    private boolean paused;
    private Stage pauseStage;
    private Texture overlayTexture;
    private InputMultiplexer inputMultiplexer;
    private boolean initialized;

    private BiomeMap biomeMap;
    private TerrainNoiseStack terrainNoise;

    public GameScreen(GalacticOdyssey game) {
        this.game = game;
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
        camera.far = 500f;

        EventBus eventBus = new EventBus();
        CoordinateManager coordinateManager = new CoordinateManager(eventBus);
        gameWorld = new GameWorld(eventBus, coordinateManager);

        Planet planet = new Planet(PLANET_SEED, PlanetType.TERRAN, 1.0f, 1.0f, 24f, 23.4f, false);

        StarSystem stubStar = new StarSystem(
            1L, PLANET_SEED, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, new Color(1f, 0.96f, 0.84f, 1f));
        OrbitalSlot stubSlot = new OrbitalSlot(0, 1.0f, 0.017f, OrbitalZone.HABITABLE);
        stubSlot.planet = planet;
        stubStar.orbits.add(stubSlot);

        AtmosphereGenerator atmoGen = new AtmosphereGenerator();
        Atmosphere atmosphere = atmoGen.generate(planet, stubStar);
        planet.atmosphere = atmosphere;

        BiomeMapper biomeMapper = new BiomeMapper();
        biomeMap = biomeMapper.generate(planet, atmosphere);

        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        terrainNoise = new TerrainNoiseStack(terrainSeed);
        planetRadius = planet.radius * 6371f;

        gameWorld.initializeSystems(camera);
        gameWorld.initAudio(game.getAudioManager());
        gameWorld.loadPlanet(planet, biomeMap);

        populatedWorld = WorldPopulator.populate(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);

        createTerrainMesh();
        createTerrainPhysics();
        createScatterBoxes();
        Vector3 spawnDir = findLandSpawnDirection();
        float height = terrainNoise.heightAt(spawnDir, biomeMap, 0);
        float spawnAlt = planetRadius + height * planetRadius * 0.01f + 2f;
        Vector3 spawnPos = new Vector3(spawnDir).scl(spawnAlt);

        camera.position.set(spawnPos);
        camera.update();

        gameWorld.createPlayerEntity(spawnPos.x, spawnPos.y, spawnPos.z);

        shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());

        Vector3 ref = Math.abs(spawnDir.y) < 0.999f ? Vector3.Y : Vector3.Z;
        Vector3 tangent = new Vector3(ref).crs(spawnDir).nor();
        Vector3 shipDir = new Vector3(spawnDir).cpy();
        shipDir.add(tangent.scl(0.002f)).nor();
        float shipHeight = terrainNoise.heightAt(shipDir, biomeMap, 0);
        float shipAlt = planetRadius + shipHeight * planetRadius * 0.01f + 3f;
        Vector3 shipPos = new Vector3(shipDir).scl(shipAlt);
        Entity ship = shipFactory.createShip(123L, ShipSizeClass.SMALL,
            shipPos.x, shipPos.y, shipPos.z);
        shipEntities.add(ship);
        buildShipMeshes();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));

        buildPauseMenu();

        Gdx.app.log("DIAG", "initializeWorld complete: planetRadius=" + planetRadius
            + " spawnPos=" + camera.position
            + " spawnAlt=" + (camera.position.len() - planetRadius));
    }

    private Vector3 findLandSpawnDirection() {
        Vector3 dir = CubeSphere.toSphere(CubeFace.POS_Z, 0.5f, 0.5f);
        for (int attempt = 0; attempt < 20; attempt++) {
            TerrainNoiseStack.Sample sample = terrainNoise.sampleAt(dir, biomeMap, 0);
            if (sample.biome != BiomeType.OCEAN && sample.biome != BiomeType.ICE_SHEET) {
                return dir;
            }
            float offsetU = 0.5f + (attempt + 1) * 0.03f;
            float offsetV = 0.5f + (attempt + 1) * 0.02f;
            dir = CubeSphere.toSphere(CubeFace.POS_Z, Math.min(offsetU, 0.95f), Math.min(offsetV, 0.95f));
        }
        return dir;
    }

    private void setupInput() {
        InputAdapter escapeHandler = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    togglePause();
                    return true;
                }
                return false;
            }
        };

        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(escapeHandler);
        if (paused) {
            inputMultiplexer.addProcessor(pauseStage);
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
            "void main() {\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    v_emissive = a_emissive;\n" +
            "    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    vec3 color = mix(lit, v_color.rgb * 2.0, v_emissive);\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        shipShader = new ShaderProgram(vert, frag);
        if (!shipShader.isCompiled()) {
            Gdx.app.error("ShipShader", shipShader.getLog());
        }
        return shipShader;
    }

    private final Matrix4 shipModelMat = new Matrix4();
    private final Vector3 shipRelPos = new Vector3();

    private void renderShips() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        ShaderProgram shader = getShipShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", terrainProjView);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            TransformComponent t = ship.getComponent(TransformComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            if (meshComp == null || meshComp.hullMesh == null) continue;

            shipRelPos.set(t.position).sub(camera.position);
            shipModelMat.set(shipRelPos, t.rotation);
            shader.setUniformMatrix("u_worldTrans", shipModelMat);

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
            "void main() {\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "void main() {\n" +
            "    vec3 n = normalize(v_normal);\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(n, lightDir), 0.0);\n" +
            "    vec3 color = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        ShaderProgram.pedantic = false;
        terrainShader = new ShaderProgram(vert, frag);
        if (!terrainShader.isCompiled()) {
            Gdx.app.error("Shader", terrainShader.getLog());
        }
        return terrainShader;
    }

    @Override
    public void render(float delta) {
        frameCount++;
        float altitude = camera.position.len() - planetRadius;
        updateCameraClipPlanes(altitude);
        updateSkyColor(altitude);

        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.getPlanetTerrainSystem().setCameraPosition(camera.position);
            gameWorld.update(clampedDelta);
            game.getAudioManager().update(clampedDelta);
            if (gameWorld.getAudioSystem() != null) {
                gameWorld.getAudioSystem().updateListener(camera.position, camera.direction);
            }
        }

        if (!paused) {
            WorldPopulator.updateAnimals(populatedWorld, delta,
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
        }

        syncBoxTransforms();
        renderTerrain();
        renderBoxes();
        renderWorldObjects();
        renderShips();

        gameWorld.getDebugHudSystem().render(delta);

        // Cockpit 3D interior — clears depth and renders over the scene
        gameWorld.getCockpitModelSystem().render(modelBatch, camera);

        if (frameCount <= 5) {
            List<TerrainChunk> leaves = gameWorld.getVisibleTerrainLeaves();
            int meshCount = 0;
            for (TerrainChunk c : leaves) { if (c.mesh != null) meshCount++; }
            com.badlogic.gdx.Gdx.app.log("DIAG", "frame=" + frameCount
                + " cam=" + camera.position
                + " alt=" + altitude
                + " leaves=" + leaves.size()
                + " withMesh=" + meshCount
                + " shaderOK=" + (terrainShader != null && terrainShader.isCompiled())
                + " near=" + camera.near + " far=" + camera.far);
        }

        if (paused) {
            pauseStage.act(delta);
            pauseStage.draw();
        }
    }

    private void updateCameraClipPlanes(float altitude) {
        if (altitude < 10f) {
            camera.near = 0.1f;
            camera.far = 500f;
        } else if (altitude < 500f) {
            camera.near = 1f;
            camera.far = altitude * 10f;
        } else {
            camera.near = 1f;
            camera.far = planetRadius * 4f;
        }
        camera.update();
    }

    private void updateSkyColor(float altitude) {
        if (altitude < ATMOSPHERE_ALTITUDE) {
            float t = MathUtils.clamp(altitude / ATMOSPHERE_ALTITUDE, 0f, 1f);
            float r = MathUtils.lerp(0.4f, 0.05f, t);
            float g = MathUtils.lerp(0.6f, 0.05f, t);
            float b = MathUtils.lerp(0.9f, 0.1f, t);
            ScreenUtils.clear(r, g, b, 1f, true);
        } else {
            ScreenUtils.clear(0.02f, 0.02f, 0.04f, 1f, true);
        }
    }

    private final Matrix4 terrainWorldTrans = new Matrix4();
    private final Matrix4 terrainProjView = new Matrix4();

    private void renderPlanetTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        terrainWorldTrans.setToTranslation(
            -camera.position.x, -camera.position.y, -camera.position.z);

        terrainProjView.set(camera.view);
        terrainProjView.val[Matrix4.M03] = 0;
        terrainProjView.val[Matrix4.M13] = 0;
        terrainProjView.val[Matrix4.M23] = 0;
        terrainProjView.mulLeft(camera.projection);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", terrainProjView);
        shader.setUniformMatrix("u_worldTrans", terrainWorldTrans);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        List<TerrainChunk> leaves = gameWorld.getVisibleTerrainLeaves();
        for (int i = 0; i < leaves.size(); i++) {
            TerrainChunk chunk = leaves.get(i);
            if (chunk.mesh != null) {
                chunk.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }

        if (frameCount <= 3) {
            int err = Gdx.gl.glGetError();
            if (err != GL20.GL_NO_ERROR) {
                Gdx.app.error("DIAG", "GL error after terrain render: 0x" + Integer.toHexString(err));
            }
            if (!leaves.isEmpty()) {
                TerrainChunk first = leaves.get(0);
                Gdx.app.log("DIAG", "chunk0: face=" + first.face + " depth=" + first.depth
                    + " numVerts=" + first.mesh.getNumVertices()
                    + " numIdx=" + first.mesh.getNumIndices()
                    + " center=" + first.center);
            }
        }
    }

    private void renderWorldObjects() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        modelBatch.begin(camera);
        for (int i = 0; i < populatedWorld.treeInstances.size; i++) {
            modelBatch.render(populatedWorld.treeInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.rockInstances.size; i++) {
            modelBatch.render(populatedWorld.rockInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.grassInstances.size; i++) {
            modelBatch.render(populatedWorld.grassInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.animalInstances.size; i++) {
            modelBatch.render(populatedWorld.animalInstances.get(i), environment);
        }
        modelBatch.end();

        if (populatedWorld.waterInstance != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDepthMask(false);
            modelBatch.begin(camera);
            modelBatch.render(populatedWorld.waterInstance, environment);
            modelBatch.end();
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        gameWorld.resize(width, height);
        pauseStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (shipFactory != null) { shipFactory.dispose(); shipFactory = null; }
        if (gameWorld != null) {
            gameWorld.dispose();
            gameWorld = null;
        }
        if (terrainShader != null) {
            terrainShader.dispose();
            terrainShader = null;
        }
        if (modelBatch != null) {
            modelBatch.dispose();
            modelBatch = null;
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
        initialized = false;
    }
}
