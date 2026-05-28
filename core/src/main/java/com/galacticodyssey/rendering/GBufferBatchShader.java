package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class GBufferBatchShader implements Shader {

    private final ShaderProgram program;
    private Camera camera;
    private final Matrix3 normalMatrix = new Matrix3();
    private final Matrix4 tmpMat = new Matrix4();

    public GBufferBatchShader(ShaderCache shaderCache) {
        this.program = shaderCache.get("gbuffer.vert", "gbuffer.frag");
    }

    @Override
    public void init() {}

    @Override
    public int compareTo(Shader other) { return 0; }

    @Override
    public boolean canRender(Renderable instance) { return true; }

    @Override
    public void begin(Camera camera, RenderContext context) {
        this.camera = camera;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        program.bind();
        program.setUniformf("u_metallicScale", 0f);
        program.setUniformf("u_roughnessScale", 0.7f);
        program.setUniformf("u_emissiveIntensity", 0f);
        program.setUniformf("u_tiling", 1f, 1f);
    }

    @Override
    public void render(Renderable renderable) {
        ColorAttribute diffuse = (ColorAttribute) renderable.material.get(ColorAttribute.Diffuse);
        if (diffuse != null) {
            program.setUniformf("u_albedoTint", diffuse.color.r, diffuse.color.g, diffuse.color.b, 1f);
        } else {
            program.setUniformf("u_albedoTint", 0.8f, 0.8f, 0.8f, 1f);
        }

        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform);

        tmpMat.set(camera.view).mul(renderable.worldTransform);
        normalMatrix.set(tmpMat).inv().transpose();
        program.setUniformMatrix("u_normalMatrix", normalMatrix);

        renderable.meshPart.render(program);
    }

    @Override
    public void end() {}

    @Override
    public void dispose() {}
}
