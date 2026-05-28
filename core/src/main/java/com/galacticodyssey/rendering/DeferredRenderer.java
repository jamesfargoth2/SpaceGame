package com.galacticodyssey.rendering;

import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
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

    private int width, height;

    public DeferredRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        this.quad = new FullscreenQuad();
        this.shaderCache = new ShaderCache();
        this.gBuffer = new GBuffer(width, height);
        this.lightingPass = new LightingPass(shaderCache, quad, width, height);
        this.forwardPass = new ForwardPass();
        this.postFX = new PostProcessingPipeline(shaderCache, quad, width, height);
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

        // Pass 1: G-Buffer
        gBuffer.begin();
        if (opaqueRenderer != null) opaqueRenderer.run();
        if (fpWeaponRenderer != null) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            fpWeaponRenderer.run();
        }
        gBuffer.end();

        // Pass 2: SSAO
        postFX.applySSAO(gBuffer, camera);

        // Pass 3: Deferred Lighting
        ImmutableArray<Entity> lights = (lightingSystem != null) ? lightingSystem.getLights() : null;
        lightingPass.resolve(gBuffer, postFX.getSSAOTexture(), camera,
            sunDirection, sunColor, sunIntensity,
            ambientColor, ambientIntensity, lights);

        // Pass 4: Forward transparents (sky, water, particles)
        forwardPass.render(lightingPass.getHDRBuffer(), camera,
            skyRenderer, waterRenderer, particleRenderer);

        // Passes 5-7: SSR -> Bloom -> Tone mapping -> FXAA -> screen
        postFX.apply(lightingPass.getHDRBuffer(), gBuffer, camera);
    }

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
    }

    @Override
    public void dispose() {
        gBuffer.dispose();
        lightingPass.dispose();
        forwardPass.dispose();
        postFX.dispose();
        shaderCache.dispose();
        quad.dispose();
    }
}
