#version 330

#include "include/normal_encoding.glsl"

in vec3 v_viewNormal;
in vec3 v_worldPos;
in vec4 v_color;
in vec3 v_objectPos;

uniform int u_patternType;
uniform vec3 u_palette[4];
uniform float u_patternScale;
uniform float u_patternContrast;
uniform float u_bioGlow;
uniform float u_roughness;
uniform float u_metallic;

layout(location = 0) out vec4 rt0_albedoMetallic;
layout(location = 1) out vec4 rt1_normalRoughnessAO;
layout(location = 2) out vec2 rt2_emissive;

float hash31(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash31(i), hash31(i + vec3(1,0,0)), f.x),
            mix(hash31(i + vec3(0,1,0)), hash31(i + vec3(1,1,0)), f.x), f.y),
        mix(mix(hash31(i + vec3(0,0,1)), hash31(i + vec3(1,0,1)), f.x),
            mix(hash31(i + vec3(0,1,1)), hash31(i + vec3(1,1,1)), f.x), f.y),
        f.z);
}

void main() {
    float dorsalVentral = v_color.r;
    float limbAxis      = v_color.g;
    float spatialHash   = v_color.b;
    float curvature     = v_color.a;

    vec3 baseColor = mix(u_palette[0], u_palette[3], dorsalVentral);

    float pattern = 0.0;
    vec3 objScaled = v_objectPos * u_patternScale;

    if (u_patternType == 1) {
        pattern = smoothstep(0.5 - u_patternContrast * 0.5, 0.5 + u_patternContrast * 0.5,
                            sin(limbAxis * u_patternScale * 3.14159 * 4.0) * 0.5 + 0.5);
    } else if (u_patternType == 2) {
        float n = noise3(objScaled * 3.0);
        pattern = smoothstep(0.55 - u_patternContrast * 0.15, 0.55, n);
    } else if (u_patternType == 3) {
        float n = noise3(objScaled * 3.0);
        float inner = smoothstep(0.55, 0.6, n);
        float outer = smoothstep(0.45, 0.5, n);
        pattern = outer - inner;
    } else if (u_patternType == 4) {
        float n = noise3(objScaled) * 0.5 + noise3(objScaled * 2.0) * 0.3 + noise3(objScaled * 4.0) * 0.2;
        pattern = smoothstep(0.4 - u_patternContrast * 0.2, 0.6 + u_patternContrast * 0.2, n);
    } else if (u_patternType == 5) {
        float n = noise3(objScaled) * 0.5 + noise3(objScaled * 2.0) * 0.5;
        pattern = smoothstep(0.4, 0.6, n);
    }

    vec3 albedo = mix(baseColor, u_palette[1], pattern);

    albedo *= mix(1.0, 0.75, limbAxis * 0.5);

    albedo += vec3(curvature * 0.03);

    float emissive = 0.0;
    if (u_patternType == 5 && u_bioGlow > 0.0) {
        float glowMask = pattern * curvature;
        emissive = glowMask * u_bioGlow;
        albedo += u_palette[2] * glowMask * u_bioGlow * 0.5;
    }

    albedo = clamp(albedo, 0.0, 1.0);

    rt0_albedoMetallic = vec4(albedo, u_metallic);
    vec2 encNormal = octEncode(normalize(v_viewNormal));
    rt1_normalRoughnessAO = vec4(encNormal, u_roughness, 1.0);
    rt2_emissive = vec2(emissive, 0.0);
}
