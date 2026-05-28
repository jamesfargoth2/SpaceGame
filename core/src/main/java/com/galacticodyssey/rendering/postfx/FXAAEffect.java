package com.galacticodyssey.rendering.postfx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.FullscreenQuad;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class FXAAEffect implements Disposable {

    private final ShaderCache shaderCache;
    private final FullscreenQuad quad;
    private int width, height;

    public FXAAEffect(ShaderCache shaderCache, FullscreenQuad quad, int width, int height) {
        this.shaderCache = shaderCache;
        this.quad = quad;
        this.width = width;
        this.height = height;
    }

    public void apply(Texture ldrInput) {
        ShaderProgram shader = shaderCache.get("fullscreen.vert", "fxaa.frag");
        shader.bind();
        ldrInput.bind(0);
        shader.setUniformi("u_inputTex", 0);
        shader.setUniformf("u_texelSize", 1f / width, 1f / height);
        quad.render(shader);
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void dispose() {}
}
