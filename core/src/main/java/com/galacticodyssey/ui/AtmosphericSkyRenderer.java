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
        "// Atmosphere geometry\n" +
        "uniform float u_planetRadius;\n" +
        "uniform float u_atmosphereRadius;\n" +
        "\n" +
        "// Scattering\n" +
        "uniform vec3 u_rayleighCoeff;\n" +
        "uniform float u_mieCoeff;\n" +
        "uniform float u_mieG;\n" +
        "uniform vec3 u_absorptionCoeff;\n" +
        "uniform float u_scaleHeightRayleigh;\n" +
        "uniform float u_scaleHeightMie;\n" +
        "\n" +
        "// Sun\n" +
        "uniform vec3 u_sunDirection;\n" +
        "uniform float u_sunIntensity;\n" +
        "uniform float u_sunAngularRadius;\n" +
        "\n" +
        "// Clouds\n" +
        "uniform float u_cloudBase;\n" +
        "uniform float u_cloudTop;\n" +
        "uniform float u_cloudCoverage;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_windDirection;\n" +
        "\n" +
        "// Camera\n" +
        "uniform vec3 u_cameraPos;\n" +
        "\n" +
        "const int PRIMARY_STEPS = 12;\n" +
        "const int LIGHT_STEPS = 4;\n" +
        "const float PI = 3.14159265;\n" +
        "\n" +
        "// Ray-sphere intersection. Returns (near, far) or (-1,-1) if no hit.\n" +
        "vec2 raySphere(vec3 origin, vec3 dir, float radius) {\n" +
        "    float b = dot(origin, dir);\n" +
        "    float c = dot(origin, origin) - radius * radius;\n" +
        "    float d = b * b - c;\n" +
        "    if (d < 0.0) return vec2(-1.0);\n" +
        "    d = sqrt(d);\n" +
        "    return vec2(-b - d, -b + d);\n" +
        "}\n" +
        "\n" +
        "// Rayleigh phase function\n" +
        "float rayleighPhase(float cosTheta) {\n" +
        "    return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);\n" +
        "}\n" +
        "\n" +
        "// Henyey-Greenstein phase function for Mie scattering\n" +
        "float miePhase(float cosTheta, float g) {\n" +
        "    float g2 = g * g;\n" +
        "    float num = (1.0 - g2);\n" +
        "    float denom = 4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);\n" +
        "    return num / denom;\n" +
        "}\n" +
        "\n" +
        "// Hash for noise\n" +
        "float hash(vec3 p) {\n" +
        "    p = fract(p * vec3(443.897, 441.423, 437.195));\n" +
        "    p += dot(p, p.yzx + 19.19);\n" +
        "    return fract((p.x + p.y) * p.z);\n" +
        "}\n" +
        "\n" +
        "// Value noise 3D\n" +
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
        "// FBM for clouds\n" +
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
        "// Cloud density at a world position\n" +
        "float cloudDensity(vec3 pos, float altitude) {\n" +
        "    if (u_cloudCoverage < 0.01) return 0.0;\n" +
        "    float cloudThickness = u_cloudTop - u_cloudBase;\n" +
        "    float heightFrac = (altitude - u_cloudBase) / cloudThickness;\n" +
        "    // Altitude ramp: peak at 0.3, fade at edges\n" +
        "    float altitudeRamp = smoothstep(0.0, 0.3, heightFrac) * smoothstep(1.0, 0.7, heightFrac);\n" +
        "    vec3 windOffset = vec3(u_windDirection.x, 0.0, u_windDirection.y) * u_time * 0.01;\n" +
        "    // Domain warp for shape variety\n" +
        "    float warp = fbm(pos * 0.001 + vec3(5.3, 1.7, 8.9) + windOffset * 0.5);\n" +
        "    float n = fbm(pos * 0.003 + windOffset + warp * 0.5);\n" +
        "    float density = smoothstep(1.0 - u_cloudCoverage, 1.0, n) * altitudeRamp;\n" +
        "    return density * 0.4;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec3 ray = normalize(v_rayDir);\n" +
        "    \n" +
        "    // Camera position on the planet surface (game units -> km scale)\n" +
        "    vec3 origin = vec3(0.0, u_planetRadius + u_cameraPos.y * 0.001, 0.0);\n" +
        "    \n" +
        "    // Intersect with atmosphere\n" +
        "    vec2 atmoHit = raySphere(origin, ray, u_atmosphereRadius);\n" +
        "    if (atmoHit.y < 0.0) {\n" +
        "        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "        return;\n" +
        "    }\n" +
        "    \n" +
        "    // Check if ray hits planet surface\n" +
        "    vec2 planetHit = raySphere(origin, ray, u_planetRadius);\n" +
        "    float maxDist = (planetHit.x > 0.0) ? planetHit.x : atmoHit.y;\n" +
        "    float startDist = max(0.0, atmoHit.x);\n" +
        "    \n" +
        "    // March through atmosphere\n" +
        "    float stepSize = (maxDist - startDist) / float(PRIMARY_STEPS);\n" +
        "    float cosTheta = dot(ray, u_sunDirection);\n" +
        "    float rPhase = rayleighPhase(cosTheta);\n" +
        "    float mPhase = miePhase(cosTheta, u_mieG);\n" +
        "    \n" +
        "    vec3 totalRayleigh = vec3(0.0);\n" +
        "    vec3 totalMie = vec3(0.0);\n" +
        "    float opticalDepthR = 0.0;\n" +
        "    float opticalDepthM = 0.0;\n" +
        "    \n" +
        "    // Cloud accumulation\n" +
        "    float cloudTransmittance = 1.0;\n" +
        "    vec3 cloudColor = vec3(0.0);\n" +
        "    \n" +
        "    for (int i = 0; i < PRIMARY_STEPS; i++) {\n" +
        "        float dist = startDist + stepSize * (float(i) + 0.5);\n" +
        "        vec3 pos = origin + ray * dist;\n" +
        "        float altitude = length(pos) - u_planetRadius;\n" +
        "        \n" +
        "        // Atmospheric density at this altitude\n" +
        "        float densityR = exp(-altitude / u_scaleHeightRayleigh) * stepSize;\n" +
        "        float densityM = exp(-altitude / u_scaleHeightMie) * stepSize;\n" +
        "        opticalDepthR += densityR;\n" +
        "        opticalDepthM += densityM;\n" +
        "        \n" +
        "        // Light march toward sun\n" +
        "        vec2 sunHit = raySphere(pos, u_sunDirection, u_atmosphereRadius);\n" +
        "        float sunStepSize = sunHit.y / float(LIGHT_STEPS);\n" +
        "        float sunOptR = 0.0;\n" +
        "        float sunOptM = 0.0;\n" +
        "        bool occluded = false;\n" +
        "        \n" +
        "        for (int j = 0; j < LIGHT_STEPS; j++) {\n" +
        "            vec3 sunPos = pos + u_sunDirection * sunStepSize * (float(j) + 0.5);\n" +
        "            float sunAlt = length(sunPos) - u_planetRadius;\n" +
        "            if (sunAlt < 0.0) { occluded = true; break; }\n" +
        "            sunOptR += exp(-sunAlt / u_scaleHeightRayleigh) * sunStepSize;\n" +
        "            sunOptM += exp(-sunAlt / u_scaleHeightMie) * sunStepSize;\n" +
        "        }\n" +
        "        \n" +
        "        if (!occluded) {\n" +
        "            vec3 tau = u_rayleighCoeff * (opticalDepthR + sunOptR) +\n" +
        "                       vec3(u_mieCoeff) * 1.1 * (opticalDepthM + sunOptM) +\n" +
        "                       u_absorptionCoeff * (opticalDepthR + sunOptR);\n" +
        "            vec3 attenuation = exp(-tau);\n" +
        "            totalRayleigh += densityR * attenuation;\n" +
        "            totalMie += densityM * attenuation;\n" +
        "        }\n" +
        "        \n" +
        "        // Cloud sampling\n" +
        "        if (altitude > u_cloudBase && altitude < u_cloudTop && cloudTransmittance > 0.01) {\n" +
        "            float cd = cloudDensity(pos, altitude);\n" +
        "            if (cd > 0.0) {\n" +
        "                // Beer-powder approximation for cloud lighting\n" +
        "                float beer = exp(-cd * stepSize * 8.0);\n" +
        "                float powder = 1.0 - exp(-cd * stepSize * 16.0);\n" +
        "                float lightEnergy = 2.0 * beer * powder;\n" +
        "                \n" +
        "                // Sun color reaching this cloud point\n" +
        "                vec3 sunTau = u_rayleighCoeff * opticalDepthR + vec3(u_mieCoeff) * opticalDepthM;\n" +
        "                vec3 sunAtten = exp(-sunTau);\n" +
        "                vec3 cloudLit = sunAtten * u_sunIntensity * lightEnergy;\n" +
        "                \n" +
        "                // Ambient from sky\n" +
        "                cloudLit += vec3(0.05, 0.07, 0.1);\n" +
        "                \n" +
        "                cloudColor += cloudTransmittance * cloudLit * cd * stepSize;\n" +
        "                cloudTransmittance *= exp(-cd * stepSize * 8.0);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    \n" +
        "    // Combine scattering\n" +
        "    vec3 sky = u_sunIntensity * (rPhase * u_rayleighCoeff * totalRayleigh +\n" +
        "                                  mPhase * vec3(u_mieCoeff) * totalMie);\n" +
        "    \n" +
        "    // Sun disc\n" +
        "    float sunCos = cos(u_sunAngularRadius);\n" +
        "    if (cosTheta > sunCos) {\n" +
        "        float edge = smoothstep(sunCos, sunCos + 0.0005, cosTheta);\n" +
        "        vec3 sunTau = u_rayleighCoeff * opticalDepthR + vec3(u_mieCoeff) * opticalDepthM;\n" +
        "        sky += exp(-sunTau) * u_sunIntensity * 4.0 * edge;\n" +
        "    }\n" +
        "    \n" +
        "    // Composite clouds over sky\n" +
        "    vec3 color = sky * cloudTransmittance + cloudColor;\n" +
        "    \n" +
        "    // Tone mapping (simple Reinhard)\n" +
        "    color = color / (1.0 + color);\n" +
        "    \n" +
        "    // Gamma correction\n" +
        "    color = pow(color, vec3(1.0 / 2.2));\n" +
        "    \n" +
        "    gl_FragColor = vec4(color, 1.0);\n" +
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
            Gdx.app.error("AtmosphericSkyRenderer", "Shader compile error:\n" + shader.getLog());
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
        invViewProj.set(camera.combined).inv();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();

        // Camera
        shader.setUniformMatrix("u_invViewProj", invViewProj);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);

        // Atmosphere geometry
        shader.setUniformf("u_planetRadius", params.planetRadius);
        shader.setUniformf("u_atmosphereRadius", params.atmosphereRadius);

        // Scattering coefficients
        shader.setUniformf("u_rayleighCoeff",
            params.rayleighCoeffR, params.rayleighCoeffG, params.rayleighCoeffB);
        shader.setUniformf("u_mieCoeff", params.mieCoeff);
        shader.setUniformf("u_mieG", params.mieG);
        shader.setUniformf("u_absorptionCoeff",
            params.absorptionCoeffR, params.absorptionCoeffG, params.absorptionCoeffB);
        shader.setUniformf("u_scaleHeightRayleigh", params.scaleHeightRayleigh);
        shader.setUniformf("u_scaleHeightMie", params.scaleHeightMie);

        // Sun
        shader.setUniformf("u_sunDirection", sunDirection.x, sunDirection.y, sunDirection.z);
        shader.setUniformf("u_sunIntensity", params.sunIntensity);
        shader.setUniformf("u_sunAngularRadius", params.sunAngularRadius);

        // Clouds
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
        // Approximate horizon color from scattering at sun altitude
        float sunAlt = sunDirection.y;
        float dayFactor = Math.max(0f, Math.min(1f, (sunAlt + 0.1f) / 0.3f));

        // Horizon color transitions with sun position
        float rR = params.rayleighCoeffR;
        float rG = params.rayleighCoeffG;
        float rB = params.rayleighCoeffB;
        float totalCoeff = rR + rG + rB + 0.0001f;

        // At noon: sky-colored horizon. At sunset: warm. At night: dark.
        float noonR = 0.5f + (1f - rR / totalCoeff) * 0.3f;
        float noonG = 0.55f + (1f - rG / totalCoeff) * 0.2f;
        float noonB = 0.6f + (rB / totalCoeff) * 0.3f;

        float sunsetR = 0.85f;
        float sunsetG = 0.45f;
        float sunsetB = 0.25f;

        float nightR = 0.02f;
        float nightG = 0.02f;
        float nightB = 0.04f;

        float sunsetFactor = (float) Math.exp(-sunAlt * sunAlt * 50f);

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
