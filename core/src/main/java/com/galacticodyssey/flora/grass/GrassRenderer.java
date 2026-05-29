package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.shaders.ShaderCache;

/** Owns the instanced grass mesh + shader and draws the active grass field in one call.
 *  Invoked inside the deferred gbuffer pass (alongside terrain). */
public final class GrassRenderer implements Disposable {
    private final ShaderCache shaderCache;
    private final GrassConfig config;
    private final Mesh mesh;
    private final int maxInstances;
    private final Matrix4 tmpView = new Matrix4();
    private final Matrix3 normalMat = new Matrix3();
    private int instanceCount;

    public GrassRenderer(ShaderCache shaderCache, GrassConfig config, int maxInstances) {
        this.shaderCache = shaderCache;
        this.config = config;
        this.maxInstances = maxInstances;
        this.mesh = GrassBladeMesh.create(config.bladesPerTuft, maxInstances);
    }

    /** Upload a new packed instance buffer (call only when the field changed). */
    public void setInstances(float[] packed, int count) {
        instanceCount = Math.min(count, maxInstances);
        if (instanceCount > 0) {
            mesh.setInstanceData(packed, 0, instanceCount * GrassCell.STRIDE);
        }
    }

    /** Render the grass field into the gbuffer. Call inside the gbuffer pass. */
    public void render(Camera camera, float time) {
        if (instanceCount <= 0) return;
        glDepthSetup();

        ShaderProgram shader = shaderCache.get("gbuffer_grass.vert", "gbuffer.frag", "HAS_VERTEX_COLOR");
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        tmpView.set(camera.view);
        normalMat.set(tmpView).inv().transpose();
        shader.setUniformMatrix("u_normalMatrix", normalMat);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_camPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fadeRadius", config.radius);
        shader.setUniformf("u_fadeBand", config.fadeBand);
        shader.setUniformf("u_windAmp", config.windAmplitude);
        shader.setUniformf("u_windFreq", config.windFrequency);

        // gbuffer.frag material uniforms (match renderTerrain)
        shader.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
        shader.setUniformf("u_metallicScale", 0f);
        shader.setUniformf("u_roughnessScale", 0.9f);
        shader.setUniformf("u_emissiveIntensity", 0f);
        shader.setUniformf("u_tiling", 1f, 1f);

        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    private static void glDepthSetup() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
    }

    @Override
    public void dispose() { mesh.dispose(); }
}
