package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Vector3;

/**
 * A {@link DefaultShaderProvider} that replaces the standard ModelBatch shader with
 * one that applies exponential-squared distance fog. Fog and lighting uniforms are
 * injected in {@link FogShader#begin(Camera, RenderContext)}; all standard libGDX
 * uniforms ({@code u_projViewTrans}, {@code u_worldTrans}, {@code u_diffuseColor})
 * are handled automatically by {@link DefaultShader}.
 *
 * <p>Typical use:
 * <pre>{@code
 * FogShaderProvider fog = new FogShaderProvider();
 * ModelBatch modelBatch = new ModelBatch(fog);
 * // optionally keep fog in sync with the sky horizon colour:
 * fog.setFogParams(0.004f, skyRenderer.horizonColor);
 * }</pre>
 */
public class FogShaderProvider extends DefaultShaderProvider {

    // -------------------------------------------------------------------------
    // Vertex shader
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // Fragment shader — exponential-squared fog + simple diffuse + ambient
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // Package-level fog / lighting state (readable by FogShader inner class)
    // -------------------------------------------------------------------------

    /** Exponential-squared fog density. Higher values produce denser fog. */
    float fogDensity = 0.004f;

    /** Fog colour; should match the sky horizon colour for a seamless transition. */
    final Vector3 fogColor   = new Vector3(0.6f, 0.55f, 0.45f);

    /** World-space light direction (points away from the scene toward the light source). */
    final Vector3 lightDir   = new Vector3(-0.4f, -0.8f, -0.3f);

    /** RGBA ambient light colour applied to all surfaces. */
    final float[] ambientColor = {0.3f, 0.3f, 0.35f, 1f};

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /** Creates the provider with default fog parameters. */
    public FogShaderProvider() {
        super(createConfig());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Updates the fog density and colour.  Call this whenever the sky changes
     * (e.g. time-of-day updates) so fog blends seamlessly with the horizon.
     *
     * @param density  exponential-squared fog density (suggested range 0.001–0.05)
     * @param fogCol   fog colour to copy (typically {@code SkyRenderer.horizonColor})
     */
    public void setFogParams(float density, Vector3 fogCol) {
        fogDensity = density;
        fogColor.set(fogCol);
    }

    // -------------------------------------------------------------------------
    // DefaultShaderProvider override
    // -------------------------------------------------------------------------

    @Override
    public Shader createShader(Renderable renderable) {
        return new FogShader(renderable, config, this);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static DefaultShader.Config createConfig() {
        DefaultShader.Config cfg = new DefaultShader.Config();
        cfg.vertexShader   = VERT_SHADER;
        cfg.fragmentShader = FRAG_SHADER;
        return cfg;
    }

    // =========================================================================
    // Inner class — FogShader
    // =========================================================================

    /**
     * Extends {@link DefaultShader} to inject fog, lighting, and camera-position
     * uniforms at the start of each batch. All standard libGDX uniforms are set
     * by the parent class.
     */
    static final class FogShader extends DefaultShader {

        private final FogShaderProvider provider;

        /**
         * @param renderable  the renderable this shader will draw
         * @param config      shader config carrying the custom GLSL strings
         * @param provider    owning provider whose fog/light state is read each frame
         */
        FogShader(Renderable renderable, DefaultShader.Config config, FogShaderProvider provider) {
            super(renderable, config);
            this.provider = provider;
        }

        /**
         * Called once per batch before any renderables are drawn.
         * Injects per-frame fog and lighting uniforms in addition to the standard
         * ones set by {@link DefaultShader#begin(Camera, RenderContext)}.
         */
        @Override
        public void begin(Camera camera, RenderContext context) {
            super.begin(camera, context);

            program.setUniformf("u_fogDensity",  provider.fogDensity);
            program.setUniformf("u_fogColor",    provider.fogColor.x,  provider.fogColor.y,  provider.fogColor.z);
            program.setUniformf("u_cameraPos",   camera.position.x,    camera.position.y,    camera.position.z);
            program.setUniformf("u_lightDir",    provider.lightDir.x,  provider.lightDir.y,  provider.lightDir.z);
            program.setUniform4fv("u_ambientColor", provider.ambientColor, 0, 4);
        }
    }
}
