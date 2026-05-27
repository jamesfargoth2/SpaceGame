package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * Renders a procedural sky behind all 3D geometry using a fullscreen quad.
 * The sky features a zenith-to-horizon gradient, atmospheric haze, a sun disc,
 * and a glow corona. Depth test and depth write are disabled during rendering
 * so the sky sits behind all subsequent geometry.
 *
 * <p>The {@link #horizonColor} field doubles as the fog color for the terrain
 * system so that the sky-to-terrain transition appears seamless.
 */
public class SkyRenderer implements Disposable {

    // -------------------------------------------------------------------------
    // Vertex shader — pass-through fullscreen quad, builds view-space ray dir
    // -------------------------------------------------------------------------
    private static final String VERT_SHADER =
        "attribute vec2 a_position;\n" +
        "uniform mat4 u_invViewProj;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "varying vec3 v_rayDir;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(a_position, 0.9999, 1.0);\n" +
        "    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);\n" +
        "    v_rayDir = farPoint.xyz / farPoint.w - u_cameraPos;\n" +
        "}\n";

    // -------------------------------------------------------------------------
    // Fragment shader — gradient, haze, sun disc, sun glow
    // -------------------------------------------------------------------------
    private static final String FRAG_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "uniform vec3 u_sunDirection;\n" +
        "uniform vec3 u_zenithColor;\n" +
        "uniform vec3 u_horizonColor;\n" +
        "uniform vec3 u_sunColor;\n" +
        "varying vec3 v_rayDir;\n" +
        "void main() {\n" +
        "    vec3 ray = normalize(v_rayDir);\n" +
        "    float altitude = ray.y;\n" +
        "    float gradientFactor = smoothstep(-0.1, 0.5, altitude);\n" +
        "    vec3 skyColor = mix(u_horizonColor, u_zenithColor, gradientFactor);\n" +
        "    float hazeFactor = exp(-abs(altitude) * 10.0);\n" +
        "    skyColor = mix(skyColor, u_horizonColor, hazeFactor * 0.5);\n" +
        "    float sunDot = dot(ray, u_sunDirection);\n" +
        "    float sunDisc = pow(max(sunDot, 0.0), 500.0);\n" +
        "    float sunGlow = pow(max(sunDot, 0.0), 8.0);\n" +
        "    skyColor += u_sunColor * sunDisc * 2.0;\n" +
        "    skyColor += u_sunColor * sunGlow * 0.15;\n" +
        "    if (altitude < 0.0) {\n" +
        "        skyColor = mix(u_horizonColor, skyColor, exp(altitude * 5.0));\n" +
        "    }\n" +
        "    gl_FragColor = vec4(skyColor, 1.0);\n" +
        "}\n";

    // -------------------------------------------------------------------------
    // Public configurable color fields
    // -------------------------------------------------------------------------

    /** Sky color at the zenith (top). Default: dark blue. */
    public final Vector3 zenithColor  = new Vector3(0.05f, 0.10f, 0.30f);

    /** Sky color at the horizon. Also used as fog color for terrain. Default: warm haze. */
    public final Vector3 horizonColor = new Vector3(0.60f, 0.55f, 0.45f);

    /** Sun disc and glow tint. Default: warm yellow-white. */
    public final Vector3 sunColor     = new Vector3(1.00f, 0.90f, 0.70f);

    // -------------------------------------------------------------------------
    // Private fields
    // -------------------------------------------------------------------------

    private final Mesh quad;
    private final ShaderProgram shader;

    /** Reusable inverse view-projection matrix to avoid per-frame allocation. */
    private final Matrix4 invViewProj = new Matrix4();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SkyRenderer() {
        quad = buildFullscreenQuad();

        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            Gdx.app.error("SkyRenderer", "Shader compile error:\n" + shader.getLog());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Renders the sky. Call this immediately after {@code ScreenUtils.clear()},
     * before any 3D geometry, so the sky sits behind everything.
     *
     * @param camera       perspective camera whose combined matrix supplies the view-projection
     * @param sunDirection normalised direction vector pointing toward the sun (world space)
     */
    public void render(PerspectiveCamera camera, Vector3 sunDirection) {
        // Compute inverse view-projection into the reusable field
        invViewProj.set(camera.combined).inv();

        // Sky must render behind everything — no depth test, no depth write
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();
        shader.setUniformMatrix("u_invViewProj",  invViewProj);
        shader.setUniformf("u_cameraPos",   camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_sunDirection", sunDirection.x,   sunDirection.y,   sunDirection.z);
        shader.setUniformf("u_zenithColor",  zenithColor.x,    zenithColor.y,    zenithColor.z);
        shader.setUniformf("u_horizonColor", horizonColor.x,   horizonColor.y,   horizonColor.z);
        shader.setUniformf("u_sunColor",     sunColor.x,       sunColor.y,       sunColor.z);

        quad.render(shader, GL20.GL_TRIANGLES);

        // Restore depth state for subsequent 3D geometry
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
    }

    /** Releases GPU resources. Must be called when the owning screen is disposed. */
    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a fullscreen quad covering NDC space.
     * Vertices are at the four corners: (-1,-1), (1,-1), (1,1), (-1,1).
     * Only a 2-component position attribute is used; two triangles cover the quad.
     */
    private static Mesh buildFullscreenQuad() {
        Mesh mesh = new Mesh(
            true,   // static — vertices never change
            4,      // vertex count
            6,      // index count
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position")
        );

        // NDC corners: bottom-left, bottom-right, top-right, top-left
        mesh.setVertices(new float[]{
            -1f, -1f,
             1f, -1f,
             1f,  1f,
            -1f,  1f
        });

        // Two triangles: (0,1,2) and (0,2,3)
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});

        return mesh;
    }
}
