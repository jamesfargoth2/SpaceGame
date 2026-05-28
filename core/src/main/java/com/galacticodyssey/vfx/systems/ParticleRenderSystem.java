package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.vfx.MeshParticle;
import com.galacticodyssey.vfx.MeshParticlePool;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.ParticleAtlasManager;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import java.util.Comparator;

public class ParticleRenderSystem extends EntitySystem implements Disposable {

    private static final int PRIORITY = 20;

    private final ParticlePoolComponent pool;
    private final Camera camera;
    private DecalBatch decalBatch;
    private TextureRegion defaultTexture;
    private ParticleAtlasManager atlasManager;
    private MeshParticlePool meshPool;
    private ModelBatch modelBatch;
    private ModelInstance fallbackMeshInstance;

    // Reusable comparator — sorts far particles first (back-to-front = far drawn first)
    private Comparator<Particle> backToFront;

    public ParticleRenderSystem(ParticlePoolComponent pool, Camera camera) {
        super(PRIORITY);
        this.pool = pool;
        this.camera = camera;
        // Initialize comparator after camera is assigned
        this.backToFront = (a, b) -> {
            float da = camera.position.dst2(a.position.x, a.position.y, a.position.z);
            float db = camera.position.dst2(b.position.x, b.position.y, b.position.z);
            return Float.compare(db, da); // descending distance
        };
    }

    public void initialize(TextureRegion defaultTexture) {
        this.defaultTexture = defaultTexture;
        if (decalBatch == null) {
            decalBatch = new DecalBatch(ParticlePoolComponent.MAX_PARTICLES,
                new CameraGroupStrategy(camera));
        }
    }

    public void setAtlasManager(ParticleAtlasManager atlasManager) {
        this.atlasManager = atlasManager;
        if (atlasManager != null) {
            initialize(atlasManager.getRegion("smoke"));
        }
    }

    public void setMeshParticlePool(MeshParticlePool meshPool, ModelBatch modelBatch,
                                     ModelInstance fallbackMeshInstance) {
        this.meshPool = meshPool;
        this.modelBatch = modelBatch;
        this.fallbackMeshInstance = fallbackMeshInstance;
    }

    @Override
    public void update(float deltaTime) {
        if (meshPool == null) return;
        for (int i = meshPool.active.size() - 1; i >= 0; i--) {
            MeshParticle mp = meshPool.active.get(i);
            mp.update(deltaTime);
            if (mp.life <= 0f) {
                meshPool.free(mp);
            }
        }
    }

    public void render() {
        renderBillboards();
        renderMeshParticles();
    }

    private void renderBillboards() {
        if (decalBatch == null || pool.active.isEmpty()) return;

        // Back-to-front sort for correct alpha blending
        pool.active.sort(backToFront);

        for (Particle p : pool.active) {
            TextureRegion tex = p.textureRegion != null ? p.textureRegion : defaultTexture;
            if (tex == null) continue;

            Decal decal = Decal.newDecal(tex, true);
            float size = p.getCurrentSize();
            decal.setDimensions(size, size);
            decal.setPosition(p.position.x, p.position.y, p.position.z);
            decal.setRotation(camera.direction.cpy().scl(-1), camera.up);

            Color c = p.getCurrentColor();
            decal.setColor(c.r, c.g, c.b, c.a);

            if ((p.flags & VFXEnums.FLAG_ADDITIVE_BLEND) != 0) {
                decal.setBlending(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            }

            decalBatch.add(decal);
        }

        decalBatch.flush();
    }

    private void renderMeshParticles() {
        if (meshPool == null || meshPool.active.isEmpty() || modelBatch == null) return;
        if (fallbackMeshInstance == null) return;

        modelBatch.begin(camera);
        for (MeshParticle mp : meshPool.active) {
            fallbackMeshInstance.transform.setToTranslation(mp.position);
            modelBatch.render(fallbackMeshInstance);
        }
        modelBatch.end();
    }

    @Override
    public void dispose() {
        if (decalBatch != null) decalBatch.dispose();
    }
}
