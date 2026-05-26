package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;

public class ParticleRenderSystem extends EntitySystem implements Disposable {
    private static final int PRIORITY = 20;
    private final ParticlePoolComponent pool;
    private final Camera camera;
    private DecalBatch decalBatch;
    private TextureRegion defaultTexture;

    public ParticleRenderSystem(ParticlePoolComponent pool, Camera camera) {
        super(PRIORITY);
        this.pool = pool;
        this.camera = camera;
    }

    public void initialize(TextureRegion defaultTexture) {
        this.defaultTexture = defaultTexture;
        this.decalBatch = new DecalBatch(ParticlePoolComponent.MAX_PARTICLES,
            new CameraGroupStrategy(camera));
    }

    @Override
    public void update(float deltaTime) {
        if (decalBatch == null) return;

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

    @Override
    public void dispose() {
        if (decalBatch != null) {
            decalBatch.dispose();
        }
    }
}
