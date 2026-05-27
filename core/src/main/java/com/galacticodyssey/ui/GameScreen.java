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
        gameWorld.loadPlanet(planet, biomeMap);

        Vector3 spawnDir = findLandSpawnDirection();
        float height = terrainNoise.heightAt(spawnDir, biomeMap, 0);
        float spawnAlt = planetRadius + height * planetRadius * 0.01f + 2f;
        Vector3 spawnPos = new Vector3(spawnDir).scl(spawnAlt);

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
    }

    private Vector3 findLandSpawnDirection() {
        Vector3 dir = CubeSphere.toSphere(CubeFace.POS_Z, 0.5f, 0.5f);
        for (int attempt = 0; attempt < 20; attempt++) {
            float lat = CubeSphere.latitudeOf(dir);
            float lon = CubeSphere.longitudeOf(dir);
            float h = terrainNoise.heightAt(dir, biomeMap, 0);
            BiomeType biome = biomeMap.getBiome(lat, lon, h);
            if (biome != BiomeType.OCEAN && biome != BiomeType.ICE_SHEET) {
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

    private void renderShips() {
        ShaderProgram shader = getShipShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

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
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 color = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
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
        float altitude = camera.position.len() - planetRadius;
        updateCameraClipPlanes(altitude);
        updateSkyColor(altitude);

        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.getPlanetTerrainSystem().setCameraPosition(camera.position);
            gameWorld.update(clampedDelta);
        }

        renderPlanetTerrain();
        renderShips();

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

    private void renderPlanetTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 identity = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", identity);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        List<TerrainChunk> leaves = gameWorld.getVisibleTerrainLeaves();
        for (int i = 0; i < leaves.size(); i++) {
            TerrainChunk chunk = leaves.get(i);
            if (chunk.mesh != null) {
                chunk.mesh.render(shader, GL20.GL_TRIANGLES);
            }
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
