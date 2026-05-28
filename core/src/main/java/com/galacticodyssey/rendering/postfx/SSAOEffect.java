package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class SSAOEffect implements Disposable {

    private static final int SAMPLE_COUNT = 32;
    private static final int NOISE_SIZE = 4;

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ssaoBuffer;
    private FrameBuffer blurTempBuffer;
    private Texture noiseTexture;
    private final float[] kernelSamples;
    private int halfWidth, halfHeight;

    public float radius = 0.5f;
    public float bias = 0.025f;

    public SSAOEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.halfWidth = width / 2;
        this.halfHeight = height / 2;
        this.kernelSamples = generateKernel();
        this.noiseTexture = generateNoiseTexture();
        createBuffers();
    }

    private void createBuffers() {
        ssaoBuffer = PostFXUtils.createR8Buffer(halfWidth, halfHeight);
        blurTempBuffer = PostFXUtils.createR8Buffer(halfWidth, halfHeight);
    }

    private float[] generateKernel() {
        float[] samples = new float[SAMPLE_COUNT * 3];
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float x = MathUtils.random(-1f, 1f);
            float y = MathUtils.random(-1f, 1f);
            float z = MathUtils.random(0f, 1f);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            x /= len; y /= len; z /= len;
            float scale = (float) i / SAMPLE_COUNT;
            scale = MathUtils.lerp(0.1f, 1f, scale * scale);
            samples[i * 3] = x * scale;
            samples[i * 3 + 1] = y * scale;
            samples[i * 3 + 2] = z * scale;
        }
        return samples;
    }

    private Texture generateNoiseTexture() {
        Pixmap pixmap = new Pixmap(NOISE_SIZE, NOISE_SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                float r = MathUtils.random(-1f, 1f) * 0.5f + 0.5f;
                float g = MathUtils.random(-1f, 1f) * 0.5f + 0.5f;
                pixmap.drawPixel(x, y, ((int)(r * 255) << 24) | ((int)(g * 255) << 16) | (128 << 8) | 255);
            }
        }
        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pixmap.dispose();
        return tex;
    }

    public void apply(Texture normalTex, Texture depthTex, PerspectiveCamera camera) {
        ssaoBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "ssao.frag");
        shader.bind();
        normalTex.bind(0);
        shader.setUniformi("u_normalTex", 0);
        depthTex.bind(1);
        shader.setUniformi("u_depthTex", 1);
        noiseTexture.bind(2);
        shader.setUniformi("u_noiseTex", 2);
        shader.setUniform3fv("u_samples[0]", kernelSamples, 0, kernelSamples.length);
        shader.setUniformi("u_sampleCount", SAMPLE_COUNT);
        shader.setUniformMatrix("u_projection", camera.projection);
        Matrix4 invProj = new Matrix4(camera.projection).inv();
        shader.setUniformMatrix("u_invProjection", invProj);
        shader.setUniformf("u_noiseScale", halfWidth / (float) NOISE_SIZE, halfHeight / (float) NOISE_SIZE);
        shader.setUniformf("u_radius", radius);
        shader.setUniformf("u_bias", bias);
        quad.render(shader);
        ssaoBuffer.end();

        blurTempBuffer.begin();
        ShaderProgram blurShader = shaderCache.get("fullscreen.vert", "blur_bilateral.frag");
        blurShader.bind();
        ssaoBuffer.getColorBufferTexture().bind(0);
        blurShader.setUniformi("u_inputTex", 0);
        blurShader.setUniformf("u_direction", 1f, 0f);
        blurShader.setUniformf("u_texelSize", 1f / halfWidth, 1f / halfHeight);
        quad.render(blurShader);
        blurTempBuffer.end();

        ssaoBuffer.begin();
        blurShader.bind();
        blurTempBuffer.getColorBufferTexture().bind(0);
        blurShader.setUniformi("u_inputTex", 0);
        blurShader.setUniformf("u_direction", 0f, 1f);
        blurShader.setUniformf("u_texelSize", 1f / halfWidth, 1f / halfHeight);
        quad.render(blurShader);
        ssaoBuffer.end();
    }

    public Texture getResult() { return ssaoBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        int newHalf = width / 2;
        int newHalfH = height / 2;
        if (newHalf == halfWidth && newHalfH == halfHeight) return;
        halfWidth = newHalf;
        halfHeight = newHalfH;
        ssaoBuffer.dispose();
        blurTempBuffer.dispose();
        createBuffers();
    }

    @Override
    public void dispose() {
        ssaoBuffer.dispose();
        blurTempBuffer.dispose();
        noiseTexture.dispose();
    }
}
