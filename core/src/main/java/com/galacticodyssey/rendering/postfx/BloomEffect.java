package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class BloomEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer[] downBuffers;
    private FrameBuffer[] upBuffers;
    private int mipLevels;
    private int width, height;

    public float threshold = 1.0f;
    public float softKnee = 0.5f;
    public float intensity = 0.3f;

    public BloomEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        createBuffers();
    }

    public static int calculateMipLevels(int width, int height) {
        int minDim = Math.min(width, height);
        int levels = 0;
        while (minDim > 22) {
            minDim /= 2;
            levels++;
        }
        return Math.max(2, levels);
    }

    private void createBuffers() {
        mipLevels = calculateMipLevels(width, height);
        downBuffers = new FrameBuffer[mipLevels];
        upBuffers = new FrameBuffer[mipLevels - 1];
        int w = width / 2;
        int h = height / 2;
        for (int i = 0; i < mipLevels; i++) {
            downBuffers[i] = PostFXUtils.createHDRBuffer(Math.max(1, w), Math.max(1, h));
            if (i < mipLevels - 1) {
                upBuffers[i] = PostFXUtils.createHDRBuffer(Math.max(1, w), Math.max(1, h));
            }
            w /= 2;
            h /= 2;
        }
    }

    public void apply(Texture hdrInput) {
        ShaderProgram downShader = shaderCache.get("fullscreen.vert", "bloom_downsample.frag");
        ShaderProgram upShader = shaderCache.get("fullscreen.vert", "bloom_upsample.frag");

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        Texture currentInput = hdrInput;
        for (int i = 0; i < mipLevels; i++) {
            downBuffers[i].begin();
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            downShader.bind();
            currentInput.bind(0);
            downShader.setUniformi("u_inputTex", 0);
            downShader.setUniformf("u_texelSize",
                1f / currentInput.getWidth(), 1f / currentInput.getHeight());
            downShader.setUniformf("u_threshold", threshold);
            downShader.setUniformf("u_softKnee", softKnee);
            downShader.setUniformi("u_applyThreshold", i == 0 ? 1 : 0);
            quad.render(downShader);
            downBuffers[i].end();
            currentInput = downBuffers[i].getColorBufferTexture();
        }

        for (int i = mipLevels - 2; i >= 0; i--) {
            Texture src = (i == mipLevels - 2)
                ? downBuffers[mipLevels - 1].getColorBufferTexture()
                : upBuffers[i + 1].getColorBufferTexture();

            upBuffers[i].begin();
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            upShader.bind();
            src.bind(0);
            upShader.setUniformi("u_inputTex", 0);
            upShader.setUniformf("u_texelSize", 1f / src.getWidth(), 1f / src.getHeight());
            upShader.setUniformf("u_intensity", 1.0f);
            quad.render(upShader);

            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
            downBuffers[i].getColorBufferTexture().bind(0);
            upShader.setUniformi("u_inputTex", 0);
            upShader.setUniformf("u_texelSize",
                1f / downBuffers[i].getWidth(), 1f / downBuffers[i].getHeight());
            upShader.setUniformf("u_intensity", 1.0f);
            quad.render(upShader);
            Gdx.gl.glDisable(GL20.GL_BLEND);

            upBuffers[i].end();
        }
    }

    public Texture getResult() {
        return upBuffers[0].getColorBufferTexture();
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        disposeBuffers();
        createBuffers();
    }

    private void disposeBuffers() {
        for (FrameBuffer fb : downBuffers) if (fb != null) fb.dispose();
        for (FrameBuffer fb : upBuffers) if (fb != null) fb.dispose();
    }

    @Override
    public void dispose() {
        disposeBuffers();
    }
}
