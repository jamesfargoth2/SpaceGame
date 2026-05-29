package com.galacticodyssey.rendering;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class EmissiveGBufferShaderProvider implements ShaderProvider {
    private final EmissiveGBufferShader shader;
    public EmissiveGBufferShaderProvider(ShaderCache shaderCache) { this.shader = new EmissiveGBufferShader(shaderCache); }
    @Override public Shader getShader(Renderable renderable) { return shader; }
    @Override public void dispose() {}
}
