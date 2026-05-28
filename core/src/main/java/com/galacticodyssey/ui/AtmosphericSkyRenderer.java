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
import com.galacticodyssey.planet.ScatteringParams;

public final class AtmosphericSkyRenderer implements Disposable {

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

    private static final String FRAG_SHADER =
        "#ifdef GL_ES\n" +
        "precision highp float;\n" +
        "#endif\n" +
        "\n" +
        "varying vec3 v_rayDir;\n" +
        "\n" +
        "uniform vec3 u_rayleighCoeff;\n" +
        "uniform float u_mieCoeff;\n" +
        "uniform float u_mieG;\n" +
        "uniform vec3 u_absorptionCoeff;\n" +
        "uniform float u_scaleHeightRayleigh;\n" +
        "uniform float u_scaleHeightMie;\n" +
        "uniform vec3 u_sunDirection;\n" +
        "uniform float u_sunIntensity;\n" +
        "uniform float u_sunAngularRadius;\n" +
        "uniform float u_cloudBase;\n" +
        "uniform float u_cloudTop;\n" +
        "uniform float u_cloudCoverage;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_windDirection;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "\n" +
        "const float PI = 3.14159265;\n" +
        "\n" +
        "float rayleighPhase(float cosTheta) {\n" +
        "    return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);\n" +
        "}\n" +
        "\n" +
        "float miePhase(float cosTheta, float g) {\n" +
        "    float g2 = g * g;\n" +
        "    float num = (1.0 - g2);\n" +
        "    float denom = 4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);\n" +
        "    return num / max(denom, 0.0001);\n" +
        "}\n" +
        "\n" +
        "float hash(vec3 p) {\n" +
        "    p = fract(p * vec3(443.897, 441.423, 437.195));\n" +
        "    p += dot(p, p.yzx + 19.19);\n" +
        "    return fract((p.x + p.y) * p.z);\n" +
        "}\n" +
        "\n" +
        "float noise3D(vec3 p) {\n" +
        "    vec3 i = floor(p);\n" +
        "    vec3 f = fract(p);\n" +
        "    f = f * f * (3.0 - 2.0 * f);\n" +
        "    return mix(\n" +
        "        mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),\n" +
        "            mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),\n" +
        "        mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),\n" +
        "            mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),\n" +
        "        f.z);\n" +
        "}\n" +
        "\n" +
        "float fbm(vec3 p) {\n" +
        "    float v = 0.0;\n" +
        "    float a = 0.5;\n" +
        "    for (int i = 0; i < 4; i++) {\n" +
        "        v += a * noise3D(p);\n" +
        "        p = p * 2.0 + vec3(1.7, 9.2, 3.1);\n" +
        "        a *= 0.5;\n" +
        "    }\n" +
        "    return v;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec3 ray = normalize(v_rayDir);\n" +
        "    float cosTheta = dot(ray, u_sunDirection);\n" +
        "    float elevation = ray.y;\n" +
        "    float sunElev = u_sunDirection.y;\n" +
        "    \n" +
        "    float viewAM = min(1.0 / (max(elevation, 0.0) + 0.05), 20.0);\n" +
        "    float sunAM = min(1.0 / (max(sunElev, 0.0) + 0.05), 20.0);\n" +
        "    \n" +
        "    // Sun optical depth (each scattering type uses its own scale height)\n" +
        "    vec3 odSunR = u_rayleighCoeff * u_scaleHeightRayleigh * sunAM;\n" +
        "    float odSunM = u_mieCoeff * u_scaleHeightMie * sunAM;\n" +
        "    vec3 odSunA = u_absorptionCoeff * u_scaleHeightRayleigh * sunAM;\n" +
        "    vec3 sunTau = odSunR + vec3(odSunM) + odSunA;\n" +
        "    vec3 sunLight = exp(-sunTau) * u_sunIntensity;\n" +
        "    \n" +
        "    float rPhase = rayleighPhase(cosTheta);\n" +
        "    float mPhase = min(miePhase(cosTheta, u_mieG), 10.0);\n" +
        "    \n" +
        "    // View optical depth (each type uses its own scale height)\n" +
        "    vec3 odViewR = u_rayleighCoeff * u_scaleHeightRayleigh * viewAM;\n" +
        "    float odViewM = u_mieCoeff * u_scaleHeightMie * viewAM;\n" +
        "    \n" +
        "    vec3 rayleighIn = sunLight * rPhase * (vec3(1.0) - exp(-odViewR));\n" +
        "    vec3 mieIn = sunLight * (mPhase * (1.0 - exp(-odViewM)) * 0.2);\n" +
        "    \n" +
        "    // Approximate multiple scattering (isotropic blue fill)\n" +
        "    vec3 msLight = sunLight * 0.01 * (vec3(1.0) - exp(-odViewR * 0.3));\n" +
        "    \n" +
        "    vec3 sky = rayleighIn + mieIn + msLight;\n" +
        "    \n" +
        "    if (elevation < 0.0) {\n" +
        "        float fade = smoothstep(0.0, -0.2, elevation);\n" +
        "        sky = mix(sky, sky * 0.35, fade);\n" +
        "    }\n" +
        "    \n" +
        "    // Sun disc\n" +
        "    float sunAngle = acos(clamp(cosTheta, -1.0, 1.0));\n" +
        "    float sunDisc = smoothstep(u_sunAngularRadius * 1.5, u_sunAngularRadius * 0.5, sunAngle);\n" +
        "    if (sunDisc > 0.0) {\n" +
        "        vec3 viewExtinct = exp(-(odViewR + vec3(odViewM)));\n" +
        "        sky += viewExtinct * sunLight * sunDisc * 0.4;\n" +
        "    }\n" +
        "    \n" +
        "    // Cloud layer\n" +
        "    if (u_cloudCoverage > 0.01 && elevation > 0.005) {\n" +
        "        float cloudMidAlt = (u_cloudBase + u_cloudTop) * 0.5;\n" +
        "        float camAltKm = u_cameraPos.y * 0.001;\n" +
        "        float heightToCloud = cloudMidAlt - camAltKm;\n" +
        "        \n" +
        "        if (heightToCloud > 0.0) {\n" +
        "            float cloudDist = heightToCloud / elevation;\n" +
        "            \n" +
        "            if (cloudDist < 80.0) {\n" +
        "                vec3 cloudWorldPos = vec3(\n" +
        "                    u_cameraPos.x * 0.001 + ray.x * cloudDist,\n" +
        "                    0.0,\n" +
        "                    u_cameraPos.z * 0.001 + ray.z * cloudDist\n" +
        "                );\n" +
        "                \n" +
        "                vec3 windOffset = vec3(u_windDirection.x, 0.0, u_windDirection.y) * u_time * 0.01;\n" +
        "                vec3 noisePos = cloudWorldPos * 0.3;\n" +
        "                float warp = fbm(noisePos * 0.3 + vec3(5.3, 1.7, 8.9) + windOffset * 0.5);\n" +
        "                float n = fbm(noisePos + windOffset + warp * 0.5);\n" +
        "                \n" +
        "                float cloudAlpha = smoothstep(1.0 - u_cloudCoverage, 1.0, n);\n" +
        "                cloudAlpha *= smoothstep(0.005, 0.15, elevation);\n" +
        "                cloudAlpha *= smoothstep(80.0, 40.0, cloudDist);\n" +
        "                cloudAlpha = min(cloudAlpha, 0.85);\n" +
        "                \n" +
        "                float cloudSunDot = max(0.0, u_sunDirection.y);\n" +
        "                vec3 cloudColor = sunLight * (0.1 + 0.1 * cloudSunDot)\n" +
        "                    + vec3(0.28, 0.32, 0.38) * (0.4 + 0.6 * cloudSunDot);\n" +
        "                sky = mix(sky, cloudColor, cloudAlpha);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    \n" +
        "    sky = vec3(1.0) - exp(-sky);\n" +
        "    sky = pow(sky, vec3(1.0 / 2.2));\n" +
        "    \n" +
        "    gl_FragColor = vec4(sky, 1.0);\n" +
        "}\n";

    private final Mesh quad;
    private final ShaderProgram shader;
    private final Matrix4 invViewProj = new Matrix4();

    private ScatteringParams params;
    private final Vector3 sunDirection = new Vector3(0f, 1f, 0f);
    private float time;

    private final Vector3 cachedHorizonColor = new Vector3(0.6f, 0.55f, 0.45f);
    private float cachedFogDensity = 0.004f;
    private float cachedAmbientIntensity = 0.35f;

    public AtmosphericSkyRenderer() {
        quad = buildFullscreenQuad();
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("AtmosphericSkyRenderer shader compile error:\n" + shader.getLog());
        }
        params = ScatteringParams.earthLike();
    }

    public void setScatteringParams(ScatteringParams params) {
        this.params = params;
        this.cachedFogDensity = params.fogDensity;
    }

    public void setSunDirection(Vector3 dir) {
        sunDirection.set(dir);
    }

    public void setTime(float elapsedSeconds) {
        this.time = elapsedSeconds;
    }

    public void render(PerspectiveCamera camera) {
        invViewProj.set(camera.invProjectionView);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();

        shader.setUniformMatrix("u_invViewProj", invViewProj);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);

        shader.setUniformf("u_rayleighCoeff",
            params.rayleighCoeffR, params.rayleighCoeffG, params.rayleighCoeffB);
        shader.setUniformf("u_mieCoeff", params.mieCoeff);
        shader.setUniformf("u_mieG", params.mieG);
        shader.setUniformf("u_absorptionCoeff",
            params.absorptionCoeffR, params.absorptionCoeffG, params.absorptionCoeffB);
        shader.setUniformf("u_scaleHeightRayleigh", params.scaleHeightRayleigh);
        shader.setUniformf("u_scaleHeightMie", params.scaleHeightMie);

        shader.setUniformf("u_sunDirection", sunDirection.x, sunDirection.y, sunDirection.z);
        shader.setUniformf("u_sunIntensity", params.sunIntensity);
        shader.setUniformf("u_sunAngularRadius", params.sunAngularRadius);

        shader.setUniformf("u_cloudBase", params.cloudBase);
        shader.setUniformf("u_cloudTop", params.cloudTop);
        shader.setUniformf("u_cloudCoverage", params.cloudCoverage);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_windDirection", 1f, 0.3f);

        quad.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        updateCachedFogValues(camera);
    }

    public Vector3 getHorizonColor() {
        return cachedHorizonColor;
    }

    public float getFogDensity() {
        return cachedFogDensity;
    }

    public Vector3 getSunDirection() {
        return sunDirection;
    }

    public float getAmbientIntensity() {
        return cachedAmbientIntensity;
    }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
    }

    private void updateCachedFogValues(PerspectiveCamera camera) {
        float sunAlt = sunDirection.y;
        float dayFactor = Math.max(0f, Math.min(1f, (sunAlt + 0.1f) / 0.3f));

        float sunsetFactor = (float) Math.exp(-sunAlt * sunAlt * 50f);

        float noonR = 0.56f;
        float noonG = 0.65f;
        float noonB = 0.72f;

        float sunsetR = 0.82f;
        float sunsetG = 0.42f;
        float sunsetB = 0.22f;

        float nightR = 0.02f;
        float nightG = 0.02f;
        float nightB = 0.04f;

        if (dayFactor > 0.01f) {
            float r = noonR * (1f - sunsetFactor) + sunsetR * sunsetFactor;
            float g = noonG * (1f - sunsetFactor) + sunsetG * sunsetFactor;
            float b = noonB * (1f - sunsetFactor) + sunsetB * sunsetFactor;
            cachedHorizonColor.set(
                r * dayFactor + nightR * (1f - dayFactor),
                g * dayFactor + nightG * (1f - dayFactor),
                b * dayFactor + nightB * (1f - dayFactor));
        } else {
            cachedHorizonColor.set(nightR, nightG, nightB);
        }

        cachedFogDensity = params.fogDensity;
        cachedAmbientIntensity = 0.06f + 0.29f * dayFactor;
    }

    private static Mesh buildFullscreenQuad() {
        Mesh mesh = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        mesh.setVertices(new float[]{-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});
        return mesh;
    }
}
