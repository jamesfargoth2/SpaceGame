package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Vector3;

public class FogShaderProvider extends DefaultShaderProvider {

    private static final String VERT_SHADER =
        "attribute vec3 a_position;\n" +
        "attribute vec3 a_normal;\n" +
        "uniform mat4 u_projViewTrans;\n" +
        "uniform mat4 u_worldTrans;\n" +
        "varying vec3 v_normal;\n" +
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
        "    v_worldPos = worldPos.xyz;\n" +
        "    v_normal = normalize(mat3(u_worldTrans[0].xyz, u_worldTrans[1].xyz, u_worldTrans[2].xyz) * a_normal);\n" +
        "    gl_Position = u_projViewTrans * worldPos;\n" +
        "}\n";

    private static final String FRAG_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "uniform vec4 u_diffuseColor;\n" +
        "uniform vec3 u_lightDir;\n" +
        "uniform vec4 u_ambientColor;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "uniform float u_fogDensity;\n" +
        "uniform vec3 u_fogColor;\n" +
        "varying vec3 v_normal;\n" +
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec3 lightDir = normalize(-u_lightDir);\n" +
        "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
        "    vec3 lit = u_diffuseColor.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
        "    float dist = length(v_worldPos - u_cameraPos);\n" +
        "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
        "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
        "    vec3 color = mix(u_fogColor, lit, fogFactor);\n" +
        "    gl_FragColor = vec4(color, u_diffuseColor.a);\n" +
        "}\n";

    float fogDensity = 0.004f;
    final Vector3 fogColor = new Vector3(0.6f, 0.55f, 0.45f);
    final Vector3 lightDir = new Vector3(-0.4f, -0.8f, -0.3f);
    final float[] ambientColor = {0.3f, 0.3f, 0.35f, 1f};

    public FogShaderProvider() {
        super(createConfig());
    }

    public void setFogParams(float density, Vector3 fogCol) {
        fogDensity = density;
        fogColor.set(fogCol);
    }

    public void setLightDir(Vector3 dir) {
        lightDir.set(dir);
    }

    public void setAmbientColor(float r, float g, float b) {
        ambientColor[0] = r;
        ambientColor[1] = g;
        ambientColor[2] = b;
    }

    @Override
    public Shader createShader(Renderable renderable) {
        return new FogShader(renderable, config, this);
    }

    private static DefaultShader.Config createConfig() {
        DefaultShader.Config cfg = new DefaultShader.Config();
        cfg.vertexShader = VERT_SHADER;
        cfg.fragmentShader = FRAG_SHADER;
        return cfg;
    }

    static final class FogShader extends DefaultShader {

        private final FogShaderProvider provider;

        FogShader(Renderable renderable, DefaultShader.Config config, FogShaderProvider provider) {
            super(renderable, config);
            this.provider = provider;
        }

        @Override
        public void begin(Camera camera, RenderContext context) {
            super.begin(camera, context);
            program.setUniformf("u_fogDensity", provider.fogDensity);
            program.setUniformf("u_fogColor", provider.fogColor.x, provider.fogColor.y, provider.fogColor.z);
            program.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
            program.setUniformf("u_lightDir", provider.lightDir.x, provider.lightDir.y, provider.lightDir.z);
            program.setUniform4fv("u_ambientColor", provider.ambientColor, 0, 4);
        }
    }
}
