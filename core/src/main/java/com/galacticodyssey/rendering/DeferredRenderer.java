package com.galacticodyssey.rendering;

import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.lighting.LightingSystem;
import com.galacticodyssey.rendering.postfx.PostProcessingPipeline;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import com.galacticodyssey.ui.AtmosphericSkyRenderer;

public class DeferredRenderer implements Disposable {

    private final GBuffer gBuffer;
    private final LightingPass lightingPass;
    private final ForwardPass forwardPass;
    private final PostProcessingPipeline postFX;
    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer forwardHDRBuffer;

    private int width, height;
    private boolean deferredEnabled = false;

    public DeferredRenderer(int width, int height) {
        ShaderProgram.pedantic = false;
        this.width = width;
        this.height = height;
        this.quad = new FullscreenQuad();
        this.shaderCache = new ShaderCache();
        this.gBuffer = new GBuffer(width, height);
        this.lightingPass = new LightingPass(shaderCache, quad, width, height);
        this.forwardPass = new ForwardPass();
        this.postFX = new PostProcessingPipeline(shaderCache, quad, width, height);
        createForwardBuffer();
    }

    private void createForwardBuffer() {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addColorTextureAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT);
        // GL_DEPTH24_STENCIL8 so we can blit GBuffer stencil here and use it to mask sky vs. geometry
        builder.addDepthRenderBuffer(GL30.GL_DEPTH24_STENCIL8);
        forwardHDRBuffer = builder.build();
        forwardHDRBuffer.getColorBufferTexture().setFilter(
            Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void render(PerspectiveCamera camera,
                       Runnable opaqueRenderer,
                       Runnable fpWeaponRenderer,
                       AtmosphericSkyRenderer skyRenderer,
                       Runnable waterRenderer,
                       Runnable particleRenderer,
                       LightingSystem lightingSystem,
                       Vector3 sunDirection, Vector3 sunColor, float sunIntensity,
                       Vector3 ambientColor, float ambientIntensity) {

        if (deferredEnabled) {
            renderDeferred(camera, opaqueRenderer, fpWeaponRenderer, skyRenderer,
                waterRenderer, particleRenderer, lightingSystem,
                sunDirection, sunColor, sunIntensity, ambientColor, ambientIntensity);
        } else {
            renderForward(camera, opaqueRenderer, fpWeaponRenderer, skyRenderer,
                waterRenderer, particleRenderer);
        }
    }

    private void renderForward(PerspectiveCamera camera,
                               Runnable opaqueRenderer,
                               Runnable fpWeaponRenderer,
                               AtmosphericSkyRenderer skyRenderer,
                               Runnable waterRenderer,
                               Runnable particleRenderer) {

        forwardHDRBuffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);

        if (opaqueRenderer != null) opaqueRenderer.run();

        if (fpWeaponRenderer != null) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            fpWeaponRenderer.run();
        }

        if (skyRenderer != null) {
            skyRenderer.render(camera);
        }

        if (waterRenderer != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            waterRenderer.run();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        if (particleRenderer != null) {
            particleRenderer.run();
        }

        forwardHDRBuffer.end();

        postFX.applyForward(forwardHDRBuffer);
    }

    private void renderDeferred(PerspectiveCamera camera,
                                Runnable opaqueRenderer,
                                Runnable fpWeaponRenderer,
                                AtmosphericSkyRenderer skyRenderer,
                                Runnable waterRenderer,
                                Runnable particleRenderer,
                                LightingSystem lightingSystem,
                                Vector3 sunDirection, Vector3 sunColor, float sunIntensity,
                                Vector3 ambientColor, float ambientIntensity) {

        gBuffer.begin();
        if (opaqueRenderer != null) opaqueRenderer.run();
        if (fpWeaponRenderer != null) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            fpWeaponRenderer.run();
        }
        gBuffer.end();

        postFX.applySSAO(gBuffer, camera);

        ImmutableArray<Entity> lights = (lightingSystem != null) ? lightingSystem.getLights() : null;
        lightingPass.resolve(gBuffer, postFX.getSSAOTexture(), camera,
            sunDirection, sunColor, sunIntensity,
            ambientColor, ambientIntensity, lights);

        // Blit GBuffer depth+stencil into forwardHDRBuffer so sky can be stencil-masked
        // against geometry pixels (stencil=1 written by GBuffer for every opaque fragment).
        Gdx.gl30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, gBuffer.getFbo().getFramebufferHandle());
        Gdx.gl30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, forwardHDRBuffer.getFramebufferHandle());
        Gdx.gl30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT, GL20.GL_NEAREST);

        forwardHDRBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);  // keep depth+stencil from the blit above
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilMask(0x00);

        // Sky fills background pixels only (stencil == 0 means no opaque geometry there)
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 0, 0xFF);
        if (skyRenderer != null) skyRenderer.render(camera);

        // Deferred lighting fills geometry pixels (stencil == 1)
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xFF);
        ShaderProgram passthrough = shaderCache.get("fullscreen.vert", "passthrough.frag");
        passthrough.bind();
        lightingPass.getHDRTexture().bind(0);
        passthrough.setUniformi("u_inputTex", 0);
        quad.render(passthrough);

        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilMask(0xFF);

        // Transparent geometry (water, particles) with depth test, no stencil restriction
        if (waterRenderer != null) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDepthMask(false);
            waterRenderer.run();
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        if (particleRenderer != null) {
            particleRenderer.run();
        }

        forwardHDRBuffer.end();
        postFX.applyForward(forwardHDRBuffer);
    }

    public void setDeferredEnabled(boolean enabled) { this.deferredEnabled = enabled; }
    public boolean isDeferredEnabled() { return deferredEnabled; }

    public void reloadShaders() {
        shaderCache.reloadAll();
    }

    public ShaderCache getShaderCache() { return shaderCache; }
    public GBuffer getGBuffer() { return gBuffer; }
    public PostProcessingPipeline getPostFX() { return postFX; }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        gBuffer.resize(width, height);
        lightingPass.resize(width, height);
        postFX.resize(width, height);
        forwardHDRBuffer.dispose();
        createForwardBuffer();
    }

    @Override
    public void dispose() {
        gBuffer.dispose();
        lightingPass.dispose();
        forwardPass.dispose();
        postFX.dispose();
        shaderCache.dispose();
        quad.dispose();
        forwardHDRBuffer.dispose();
    }
}
