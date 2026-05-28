package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class SSREffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ssrBuffer;
    private int width, height;

    public float maxDistance = 50f;
    public float thickness = 0.1f;
    public int maxSteps = 16;

    public SSREffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        ssrBuffer = PostFXUtils.createHDRBuffer(width, height);
    }

    public void apply(Texture hdrTex, Texture rt0, Texture rt1, Texture depthTex, PerspectiveCamera camera) {
        ssrBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "ssr.frag");
        shader.bind();
        hdrTex.bind(0);
        shader.setUniformi("u_hdrTex", 0);
        rt0.bind(1);
        shader.setUniformi("u_rt0", 1);
        rt1.bind(2);
        shader.setUniformi("u_rt1", 2);
        depthTex.bind(3);
        shader.setUniformi("u_depthTex", 3);
        shader.setUniformMatrix("u_projection", camera.projection);
        Matrix4 invProj = new Matrix4(camera.projection).inv();
        shader.setUniformMatrix("u_invProjection", invProj);
        shader.setUniformf("u_maxDistance", maxDistance);
        shader.setUniformf("u_thickness", thickness);
        shader.setUniformi("u_maxSteps", maxSteps);
        quad.render(shader);
        ssrBuffer.end();
    }

    public Texture getResult() { return ssrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        ssrBuffer.dispose();
        ssrBuffer = PostFXUtils.createHDRBuffer(width, height);
    }

    @Override
    public void dispose() {
        ssrBuffer.dispose();
    }
}
