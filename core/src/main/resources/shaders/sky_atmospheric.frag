#ifdef GL_ES
precision highp float;
#endif

varying vec3 v_rayDir;

uniform vec3 u_rayleighCoeff;
uniform float u_mieCoeff;
uniform float u_mieG;
uniform vec3 u_absorptionCoeff;
uniform float u_scaleHeightRayleigh;
uniform float u_scaleHeightMie;
uniform vec3 u_sunDirection;
uniform float u_sunIntensity;
uniform float u_sunAngularRadius;
uniform float u_cloudBase;
uniform float u_cloudTop;
uniform float u_cloudCoverage;
uniform float u_time;
uniform vec2 u_windDirection;
uniform vec3 u_cameraPos;

const float PI = 3.14159265;

float rayleighPhase(float cosTheta) {
    return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);
}

float miePhase(float cosTheta, float g) {
    float g2 = g * g;
    float num = (1.0 - g2);
    float denom = 4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);
    return num / max(denom, 0.0001);
}

float hash(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
            mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
        mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
            mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),
        f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise3D(p);
        p = p * 2.0 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec3 ray = normalize(v_rayDir);
    float cosTheta = dot(ray, u_sunDirection);
    float elevation = ray.y;
    float sunElev = u_sunDirection.y;

    float viewAM = min(1.0 / (max(elevation, 0.0) + 0.05), 20.0);
    float sunAM = min(1.0 / (max(sunElev, 0.0) + 0.05), 20.0);

    // Sun optical depth (each scattering type uses its own scale height)
    vec3 odSunR = u_rayleighCoeff * u_scaleHeightRayleigh * sunAM;
    float odSunM = u_mieCoeff * u_scaleHeightMie * sunAM;
    vec3 odSunA = u_absorptionCoeff * u_scaleHeightRayleigh * sunAM;
    vec3 sunTau = odSunR + vec3(odSunM) + odSunA;
    vec3 sunLight = exp(-sunTau) * u_sunIntensity;

    float rPhase = rayleighPhase(cosTheta);
    float mPhase = min(miePhase(cosTheta, u_mieG), 10.0);

    // View optical depth (each type uses its own scale height)
    vec3 odViewR = u_rayleighCoeff * u_scaleHeightRayleigh * viewAM;
    float odViewM = u_mieCoeff * u_scaleHeightMie * viewAM;

    vec3 rayleighIn = sunLight * rPhase * (vec3(1.0) - exp(-odViewR));
    vec3 mieIn = sunLight * (mPhase * (1.0 - exp(-odViewM)) * 0.2);

    // Approximate multiple scattering (isotropic blue fill)
    vec3 msLight = sunLight * 0.01 * (vec3(1.0) - exp(-odViewR * 0.3));

    vec3 sky = rayleighIn + mieIn + msLight;

    if (elevation < 0.0) {
        float fade = smoothstep(0.0, -0.2, elevation);
        sky = mix(sky, sky * 0.35, fade);
    }

    // Sun disc
    float sunAngle = acos(clamp(cosTheta, -1.0, 1.0));
    float sunDisc = smoothstep(u_sunAngularRadius * 1.5, u_sunAngularRadius * 0.5, sunAngle);
    if (sunDisc > 0.0) {
        vec3 viewExtinct = exp(-(odViewR + vec3(odViewM)));
        sky += viewExtinct * sunLight * sunDisc * 0.4;
    }

    // Cloud layer
    if (u_cloudCoverage > 0.01 && elevation > 0.005) {
        float cloudMidAlt = (u_cloudBase + u_cloudTop) * 0.5;
        float camAltKm = u_cameraPos.y * 0.001;
        float heightToCloud = cloudMidAlt - camAltKm;

        if (heightToCloud > 0.0) {
            float cloudDist = heightToCloud / elevation;

            if (cloudDist < 80.0) {
                vec3 cloudWorldPos = vec3(
                    u_cameraPos.x * 0.001 + ray.x * cloudDist,
                    0.0,
                    u_cameraPos.z * 0.001 + ray.z * cloudDist
                );

                vec3 windOffset = vec3(u_windDirection.x, 0.0, u_windDirection.y) * u_time * 0.01;
                vec3 noisePos = cloudWorldPos * 0.3;
                float warp = fbm(noisePos * 0.3 + vec3(5.3, 1.7, 8.9) + windOffset * 0.5);
                float n = fbm(noisePos + windOffset + warp * 0.5);

                float cloudAlpha = smoothstep(1.0 - u_cloudCoverage, 1.0, n);
                cloudAlpha *= smoothstep(0.005, 0.15, elevation);
                cloudAlpha *= smoothstep(80.0, 40.0, cloudDist);
                cloudAlpha = min(cloudAlpha, 0.85);

                float cloudSunDot = max(0.0, u_sunDirection.y);
                vec3 cloudColor = sunLight * (0.1 + 0.1 * cloudSunDot)
                    + vec3(0.28, 0.32, 0.38) * (0.4 + 0.6 * cloudSunDot);
                sky = mix(sky, cloudColor, cloudAlpha);
            }
        }
    }

    sky = vec3(1.0) - exp(-sky);
    sky = pow(sky, vec3(1.0 / 2.2));

    gl_FragColor = vec4(sky, 1.0);
}
