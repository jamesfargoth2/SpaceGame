package com.galacticodyssey.rendering;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class GBufferBatchShaderProvider implements ShaderProvider {

    private final GBufferBatchShader shader;

    public GBufferBatchShaderProvider(ShaderCache shaderCache) {
        this.shader = new GBufferBatchShader(shaderCache);
    }

    @Override
    public Shader getShader(Renderable renderable) {
        return shader;
    }

    @Override
    public void dispose() {}
}
