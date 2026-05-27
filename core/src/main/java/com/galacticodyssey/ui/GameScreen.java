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
import com.badlogic.gdx.math.Interpolation;
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
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.WorldPopulator;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipFactory;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipMeshComponent;

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
    private GameWorld gameWorld;
    private PerspectiveCamera camera;
    private float[] heightmap;

    private ShipFactory shipFactory;
    private final Array<Entity> shipEntities = new Array<>();
    private ShaderProgram shipShader;

    private Mesh terrainMesh;
    private ShaderProgram terrainShader;
    private ModelBatch modelBatch;
    private Environment environment;
    private final Array<ModelInstance> boxInstances = new Array<>();
    private final Array<Entity> boxEntities = new Array<>();
    private final Array<Disposable> disposables = new Array<>();

    private WorldPopulator.PopulatedWorld populatedWorld;

    private static final float FOG_DENSITY = 0.004f;
    private float fogDensity = FOG_DENSITY;
    private final Vector3 horizonColor = new Vector3(0.6f, 0.55f, 0.45f);
    private final Vector3 sunDirection = new Vector3(-0.4f, -0.8f, -0.3f).nor();

    private SkyRenderer skyRenderer;
    private FogShaderProvider fogShaderProvider;
    private ModelBatch fogModelBatch;

    private boolean paused;
    private Stage pauseStage;
    private Texture overlayTexture;
    private InputMultiplexer inputMultiplexer;
    private boolean initialized;

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
        camera.far = 5000f;

        EventBus eventBus = new EventBus();
        CoordinateManager coordinateManager = new CoordinateManager(eventBus);
        gameWorld = new GameWorld(eventBus, coordinateManager);

        heightmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);

        gameWorld.initializeSystems(camera);

        populatedWorld = WorldPopulator.populate(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);

        createTerrainMesh();
        createTerrainPhysics();
        createScatterBoxes();

        float spawnHeight = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
        gameWorld.createPlayerEntity(0, spawnHeight, 0);

        shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());

        float smallX = 10f, smallZ = 10f;
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

        buildShipMeshes();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));

        buildPauseMenu();

        skyRenderer = new SkyRenderer();
        fogShaderProvider = new FogShaderProvider();
        fogModelBatch = new ModelBatch(fogShaderProvider);
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

    private void renderShips() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        ShaderProgram shader = getShipShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fogDensity", fogDensity);
        shader.setUniformf("u_fogColor", horizonColor.x, horizonColor.y, horizonColor.z);

        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            TransformComponent t = ship.getComponent(TransformComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            if (meshComp == null || meshComp.hullMesh == null) continue;

            Matrix4 modelMat = new Matrix4();
            modelMat.set(t.position, t.rotation);
            shader.setUniformMatrix("u_worldTrans", modelMat);

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
        ScreenUtils.clear(0.05f, 0.05f, 0.1f, 1f, true);

        skyRenderer.render(camera, sunDirection);

        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.update(clampedDelta);
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

        if (paused) {
            pauseStage.act(delta);
            pauseStage.draw();
        }
    }

    private void syncBoxTransforms() {
        for (int i = 0; i < boxEntities.size; i++) {
            Entity entity = boxEntities.get(i);
            TransformComponent t = entity.getComponent(TransformComponent.class);
            boxInstances.get(i).transform.setToTranslation(t.position);
            boxInstances.get(i).transform.rotate(t.rotation);
        }
    }

    private void renderTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 modelMat = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", modelMat);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fogDensity", fogDensity);
        shader.setUniformf("u_fogColor", horizonColor.x, horizonColor.y, horizonColor.z);

        terrainMesh.render(shader, GL20.GL_TRIANGLES);
    }

    private void renderBoxes() {
        fogShaderProvider.setFogParams(fogDensity, horizonColor);
        fogModelBatch.begin(camera);
        for (int i = 0; i < boxInstances.size; i++) {
            fogModelBatch.render(boxInstances.get(i), environment);
        }
        fogModelBatch.end();
    }

    private void renderWorldObjects() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        fogShaderProvider.setFogParams(fogDensity, horizonColor);
        fogModelBatch.begin(camera);
        for (int i = 0; i < populatedWorld.treeInstances.size; i++) {
            fogModelBatch.render(populatedWorld.treeInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.rockInstances.size; i++) {
            fogModelBatch.render(populatedWorld.rockInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.grassInstances.size; i++) {
            fogModelBatch.render(populatedWorld.grassInstances.get(i), environment);
        }
        for (int i = 0; i < populatedWorld.animalInstances.size; i++) {
            fogModelBatch.render(populatedWorld.animalInstances.get(i), environment);
        }
        fogModelBatch.end();

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
        if (fogModelBatch != null) {
            fogModelBatch.dispose();
            fogModelBatch = null;
        }
        if (skyRenderer != null) {
            skyRenderer.dispose();
            skyRenderer = null;
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
        boxInstances.clear();
        boxEntities.clear();
        initialized = false;
    }
}
