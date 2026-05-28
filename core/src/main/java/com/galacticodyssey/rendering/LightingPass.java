package com.galacticodyssey.rendering;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.rendering.lighting.LightComponent;
import com.galacticodyssey.rendering.lighting.LightVolumeMesh;
import com.galacticodyssey.rendering.lighting.LightingSystem;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import com.badlogic.gdx.utils.Disposable;

public class LightingPass implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private final LightVolumeMesh lightVolume;
    private FrameBuffer hdrBuffer;
    private int width, height;

    private final Matrix4 invProjection = new Matrix4();
    private final Vector3 viewLightDir = new Vector3();

    public LightingPass(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.lightVolume = new LightVolumeMesh(16, 8);
        this.width = width;
        this.height = height;
        createHDRBuffer();
    }

    private void createHDRBuffer() {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        builder.addDepthRenderBuffer(GL30.GL_DEPTH24_STENCIL8);
        hdrBuffer = builder.build();
        hdrBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void resolve(GBuffer gBuffer, Texture ssaoTexture, PerspectiveCamera camera,
                        Vector3 sunDirection, Vector3 sunColor, float sunIntensity,
                        Vector3 ambientColor, float ambientIntensity,
                        ImmutableArray<Entity> lights) {

        invProjection.set(camera.projection).inv();
        viewLightDir.set(sunDirection).rot(camera.view).nor();

        hdrBuffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        ShaderProgram dirShader = shaderCache.get("fullscreen.vert", "lighting_directional.frag");
        dirShader.bind();
        gBuffer.getAlbedoMetallic().bind(0);
        dirShader.setUniformi("u_rt0", 0);
        gBuffer.getNormalRoughnessAO().bind(1);
        dirShader.setUniformi("u_rt1", 1);
        gBuffer.getEmissive().bind(2);
        dirShader.setUniformi("u_rt2", 2);
        ssaoTexture.bind(4);
        dirShader.setUniformi("u_ssao", 4);
        dirShader.setUniformMatrix("u_invProjection", invProjection);
        dirShader.setUniformf("u_lightDir", viewLightDir.x, viewLightDir.y, viewLightDir.z);
        dirShader.setUniformf("u_lightColor", sunColor.x, sunColor.y, sunColor.z);
        dirShader.setUniformf("u_lightIntensity", sunIntensity);
        dirShader.setUniformf("u_ambientColor", ambientColor.x, ambientColor.y, ambientColor.z);
        dirShader.setUniformf("u_ambientIntensity", ambientIntensity);
        quad.render(dirShader);

        if (lights != null && lights.size() > 0) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

            for (int i = 0; i < lights.size(); i++) {
                Entity entity = lights.get(i);
                LightComponent light = LightingSystem.getLight(entity);
                if (light == null || light.type == LightComponent.Type.DIRECTIONAL) continue;
            }

            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        hdrBuffer.end();
    }

    public FrameBuffer getHDRBuffer() { return hdrBuffer; }
    public Texture getHDRTexture() { return hdrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        hdrBuffer.dispose();
        createHDRBuffer();
    }

    @Override
    public void dispose() {
        hdrBuffer.dispose();
        lightVolume.dispose();
    }
}
