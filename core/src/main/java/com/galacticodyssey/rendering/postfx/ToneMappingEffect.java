package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class ToneMappingEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private FrameBuffer ldrBuffer;
    private int width, height;

    public float exposure = 1.0f;

    public ToneMappingEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
        ldrBuffer = PostFXUtils.createLDRBuffer(width, height);
    }

    public void apply(Texture hdrInput) {
        ldrBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "tonemap.frag");
        shader.bind();
        hdrInput.bind(0);
        shader.setUniformi("u_hdrTex", 0);
        shader.setUniformf("u_exposure", exposure);
        quad.render(shader);
        ldrBuffer.end();
    }

    public Texture getResult() { return ldrBuffer.getColorBufferTexture(); }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        ldrBuffer.dispose();
        ldrBuffer = PostFXUtils.createLDRBuffer(width, height);
    }

    @Override
    public void dispose() {
        ldrBuffer.dispose();
    }
}
