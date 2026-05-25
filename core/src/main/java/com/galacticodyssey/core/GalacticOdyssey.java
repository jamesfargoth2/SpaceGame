package com.galacticodyssey.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btHeightfieldTerrainShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.galacticodyssey.data.TerrainGenerator;

import java.nio.FloatBuffer;
import java.util.Random;

public class GalacticOdyssey extends ApplicationAdapter {

    private static final int TERRAIN_VERTS_X = 257;
    private static final int TERRAIN_VERTS_Z = 257;
    private static final float TERRAIN_WIDTH = 500f;
    private static final float TERRAIN_DEPTH = 500f;
    private static final long TERRAIN_SEED = 42L;

    private EventBus eventBus;
    private CoordinateManager coordinateManager;
    private GameWorld gameWorld;
    private PerspectiveCamera camera;

    private Mesh terrainMesh;
    private float[] heightmap;

    private ModelBatch modelBatch;
    private Environment environment;
    private Array<ModelInstance> boxInstances = new Array<>();
    private Array<Model> boxModels = new Array<>();
    private Array<com.badlogic.ashley.core.Entity> boxEntities = new Array<>();

    private btHeightfieldTerrainShape terrainShape;
    private FloatBuffer heightmapBuffer;

    @Override
    public void create() {
        Bullet.init();

        eventBus = new EventBus();
        coordinateManager = new CoordinateManager(eventBus);

        heightmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);

        gameWorld = new GameWorld(eventBus, coordinateManager);

        camera = new PerspectiveCamera(75, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 5000f;

        gameWorld.initializeSystems(camera);

        createTerrainMesh();
        createTerrainPhysics();
        createScatterBoxes();

        float spawnHeight = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
        gameWorld.createPlayerEntity(0, spawnHeight, 0);

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));

        Gdx.app.log("GalacticOdyssey", "Galactic Odyssey started.");
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

                vertices[vi]     = x * cellW - halfW;
                vertices[vi + 1] = h;
                vertices[vi + 2] = z * cellD - halfD;

                vertices[vi + 3] = normals[idx * 3];
                vertices[vi + 4] = normals[idx * 3 + 1];
                vertices[vi + 5] = normals[idx * 3 + 2];

                float slope = 1f - normals[idx * 3 + 1];
                float heightFrac = (h - minH) / (maxH - minH + 0.001f);
                float r = 0.2f + slope * 0.4f + heightFrac * 0.1f;
                float g = 0.4f - slope * 0.2f + heightFrac * 0.05f;
                float b = 0.1f + heightFrac * 0.05f;

                vertices[vi + 6] = r;
                vertices[vi + 7] = g;
                vertices[vi + 8] = b;
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
        heightmapBuffer = BufferUtils.newFloatBuffer(heightmap.length);
        heightmapBuffer.put(heightmap);
        heightmapBuffer.flip();

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float h : heightmap) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        // Constructor: (heightStickWidth, heightStickLength, FloatBuffer, heightScale, minHeight, maxHeight, upAxis, useFloatData)
        terrainShape = new btHeightfieldTerrainShape(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, heightmapBuffer,
            1f, minH, maxH, 1, false);

        Vector3 localScale = new Vector3(
            TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1),
            1f,
            TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1));
        terrainShape.setLocalScaling(localScale);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrainBody = new btRigidBody(info);

        float midH = (minH + maxH) / 2f;
        terrainBody.setWorldTransform(new Matrix4().setToTranslation(0, midH, 0));
        terrainBody.setFriction(0.9f);
        info.dispose();

        gameWorld.addTerrainBody(terrainBody);
    }

    private void createScatterBoxes() {
        Random rng = new Random(123L);
        ModelBuilder modelBuilder = new ModelBuilder();

        for (int i = 0; i < 15; i++) {
            float halfExt = 0.5f + rng.nextFloat() * 1.0f;
            float bx = (rng.nextFloat() - 0.5f) * TERRAIN_WIDTH * 0.6f;
            float bz = (rng.nextFloat() - 0.5f) * TERRAIN_DEPTH * 0.6f;
            float by = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, bx, bz)
                + halfExt + 1f;
            float mass = 50f + rng.nextFloat() * 150f;

            com.badlogic.ashley.core.Entity boxEntity = gameWorld.createDynamicBox(bx, by, bz, halfExt, mass);
            boxEntities.add(boxEntity);

            float r = 0.3f + rng.nextFloat() * 0.7f;
            float g = 0.3f + rng.nextFloat() * 0.7f;
            float b = 0.3f + rng.nextFloat() * 0.7f;

            // createBox requires long for vertex attribute usage flags
            Model boxModel = modelBuilder.createBox(
                halfExt * 2, halfExt * 2, halfExt * 2,
                new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f))),
                (long) (VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
            boxModels.add(boxModel);

            ModelInstance instance = new ModelInstance(boxModel);
            instance.transform.setToTranslation(bx, by, bz);
            boxInstances.add(instance);
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f, true);

        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        gameWorld.update(delta);

        syncBoxTransforms();
        renderTerrain();
        renderBoxes();
    }

    private void syncBoxTransforms() {
        for (int i = 0; i < boxEntities.size; i++) {
            com.badlogic.ashley.core.Entity entity = boxEntities.get(i);
            com.galacticodyssey.core.components.TransformComponent t =
                entity.getComponent(com.galacticodyssey.core.components.TransformComponent.class);
            boxInstances.get(i).transform.setToTranslation(t.position);
            boxInstances.get(i).transform.rotate(t.rotation);
        }
    }

    private void renderTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 modelMat = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", modelMat);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        terrainMesh.render(shader, GL20.GL_TRIANGLES);
    }

    private void renderBoxes() {
        modelBatch.begin(camera);
        for (ModelInstance instance : boxInstances) {
            modelBatch.render(instance, environment);
        }
        modelBatch.end();
    }

    private ShaderProgram terrainShader;

    private ShaderProgram getTerrainShader() {
        if (terrainShader != null) return terrainShader;
        terrainShader = createTerrainShader();
        return terrainShader;
    }

    private ShaderProgram createTerrainShader() {
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

        ShaderProgram shader = new ShaderProgram(vert, frag);
        if (!shader.isCompiled()) {
            Gdx.app.error("Shader", shader.getLog());
        }
        return shader;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        gameWorld.resize(width, height);
    }

    @Override
    public void dispose() {
        gameWorld.dispose();
        if (terrainMesh != null) terrainMesh.dispose();
        if (terrainShader != null) terrainShader.dispose();
        if (modelBatch != null) modelBatch.dispose();
        for (Model m : boxModels) m.dispose();
        if (terrainShape != null) terrainShape.dispose();
        Gdx.app.log("GalacticOdyssey", "Shutting down.");
    }
}
