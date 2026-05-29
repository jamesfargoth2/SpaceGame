package com.galacticodyssey.fauna.skin;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.galacticodyssey.rendering.shaders.ShaderUtils;

public class CreatureSkinShader implements Shader {
    private ShaderProgram program;
    private Camera camera;
    private final Matrix3 tmpMat3 = new Matrix3();
    private final Matrix4 tmpMat4 = new Matrix4();

    private int u_projViewTrans, u_worldTrans, u_normalMatrix;
    private int u_patternType, u_patternScale, u_patternContrast, u_bioGlow;
    private int u_roughness, u_metallic;
    private int u_palette;

    @Override
    public void init() {
        String vert = ShaderUtils.loadShader("shaders/creature_skin.vert");
        String frag = ShaderUtils.loadShader("shaders/creature_skin.frag");
        frag = ShaderUtils.resolveIncludes(frag, "shaders/");
        program = new ShaderProgram(vert, frag);
        if (!program.isCompiled()) {
            throw new GdxRuntimeException("Creature skin shader failed: " + program.getLog());
        }
        u_projViewTrans = program.getUniformLocation("u_projViewTrans");
        u_worldTrans = program.getUniformLocation("u_worldTrans");
        u_normalMatrix = program.getUniformLocation("u_normalMatrix");
        u_patternType = program.getUniformLocation("u_patternType");
        u_patternScale = program.getUniformLocation("u_patternScale");
        u_patternContrast = program.getUniformLocation("u_patternContrast");
        u_bioGlow = program.getUniformLocation("u_bioGlow");
        u_roughness = program.getUniformLocation("u_roughness");
        u_metallic = program.getUniformLocation("u_metallic");
        u_palette = program.getUniformLocation("u_palette[0]");
    }

    @Override
    public int compareTo(Shader other) { return 0; }

    @Override
    public boolean canRender(Renderable instance) { return true; }

    @Override
    public void begin(Camera camera, RenderContext context) {
        this.camera = camera;
        program.bind();
        program.setUniformMatrix(u_projViewTrans, camera.combined);
    }

    @Override
    public void render(Renderable renderable) {
        program.setUniformMatrix(u_worldTrans, renderable.worldTransform);

        tmpMat4.set(camera.view).mul(renderable.worldTransform);
        tmpMat3.set(tmpMat4).inv().transpose();
        program.setUniformMatrix(u_normalMatrix, tmpMat3);

        CreatureSkinSpec skin = null;
        if (renderable.userData instanceof CreatureSkinSpec) {
            skin = (CreatureSkinSpec) renderable.userData;
        }

        if (skin != null) {
            program.setUniformi(u_patternType, skin.patternType.shaderId);
            program.setUniformf(u_patternScale, skin.patternScale);
            program.setUniformf(u_patternContrast, skin.patternContrast);
            program.setUniformf(u_bioGlow, skin.bioGlow);
            program.setUniformf(u_roughness, skin.roughness);
            program.setUniformf(u_metallic, skin.metallic);

            float[] palette = new float[] {
                skin.primaryR, skin.primaryG, skin.primaryB,
                skin.secondaryR, skin.secondaryG, skin.secondaryB,
                skin.accentR, skin.accentG, skin.accentB,
                skin.ventralR, skin.ventralG, skin.ventralB
            };
            program.setUniform3fv(u_palette, palette, 0, 12);
        } else {
            program.setUniformi(u_patternType, 0);
            program.setUniformf(u_roughness, 0.7f);
            program.setUniformf(u_metallic, 0f);
            program.setUniformf(u_bioGlow, 0f);
        }

        renderable.meshPart.render(program);
    }

    @Override
    public void end() {}

    @Override
    public void dispose() {
        if (program != null) program.dispose();
    }
}
