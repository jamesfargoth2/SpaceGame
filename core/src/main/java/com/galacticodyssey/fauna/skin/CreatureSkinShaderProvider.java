package com.galacticodyssey.fauna.skin;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;

public class CreatureSkinShaderProvider implements ShaderProvider {
    private CreatureSkinShader shader;

    @Override
    public Shader getShader(Renderable renderable) {
        if (shader == null) {
            shader = new CreatureSkinShader();
            shader.init();
        }
        return shader;
    }

    @Override
    public void dispose() {
        if (shader != null) shader.dispose();
    }
}
