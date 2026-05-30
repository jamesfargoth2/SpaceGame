#version 330

#include "include/normal_encoding.glsl"

in vec3 v_worldPos;
in vec3 v_worldNormal;
in vec3 v_viewNormal;
in vec4 v_color;

uniform sampler2D u_grassTex;
uniform sampler2D u_dirtTex;
uniform sampler2D u_rockTex;
uniform sampler2D u_gravelTex;

uniform float u_texScale;
uniform bool u_darkBackfaces;
uniform mat3 u_normalMatrix;

#ifdef TERRAIN_FADE
// 0=fully transparent, 1=fully opaque. Drives screen-door dither for altitude fade.
uniform float u_terrainFade;
#endif

layout(location = 0) out vec4 rt0_albedoMetallic;
layout(location = 1) out vec4 rt1_normalRoughnessAO;
layout(location = 2) out vec4 rt2_emissive;

// Simple hash for procedural variation without extra textures
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise2d(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p, int octaves) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < octaves; i++) {
        v += a * noise2d(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

vec3 triplanar(sampler2D tex, vec3 pos, vec3 blend, float scale) {
    return texture(tex, pos.yz * scale).rgb * blend.x
         + texture(tex, pos.xz * scale).rgb * blend.y
         + texture(tex, pos.xy * scale).rgb * blend.z;
}

void main() {
    vec3 N = normalize(v_worldNormal);

    // Tri-planar blend weights
    vec3 blend = pow(abs(N), vec3(4.0));
    blend /= dot(blend, vec3(1.0));

    // Two-scale sampling to break tiling repetition
    float s = u_texScale;
    vec3 grass  = mix(triplanar(u_grassTex,  v_worldPos, blend, s),
                      triplanar(u_grassTex,  v_worldPos, blend, s * 0.23), 0.3);
    vec3 dirt   = mix(triplanar(u_dirtTex,   v_worldPos, blend, s * 0.7),
                      triplanar(u_dirtTex,   v_worldPos, blend, s * 0.13), 0.3);
    vec3 rock   = mix(triplanar(u_rockTex,   v_worldPos, blend, s * 0.5),
                      triplanar(u_rockTex,   v_worldPos, blend, s * 0.1), 0.3);
    vec3 gravel = mix(triplanar(u_gravelTex, v_worldPos, blend, s * 0.8),
                      triplanar(u_gravelTex, v_worldPos, blend, s * 0.15), 0.3);

    // ---- Splatmap weights from slope, biome colour, and procedural noise ----
    float slope = 1.0 - N.y;
    vec3 bc = v_color.rgb;

    // Analyse biome vertex colour
    float greenness = bc.g - max(bc.r, bc.b);
    float brownness = bc.r - bc.g;
    float saturation = max(bc.r, max(bc.g, bc.b))
                     - min(bc.r, min(bc.g, bc.b));
    float brightness = dot(bc, vec3(0.333));

    // Procedural noise for organic patch variation
    vec2 wp = v_worldPos.xz;
    float patchNoise = fbm(wp * 0.08, 4);
    float fineNoise  = fbm(wp * 0.3 + 50.0, 3);
    float microNoise = noise2d(wp * 1.5 + 100.0);

    // Base material affinity
    float wG = max(0.0, smoothstep(-0.02, 0.12, greenness));
    float wD = max(0.0, smoothstep(-0.02, 0.12, brownness));
    float wV = max(0.0, (1.0 - saturation) * smoothstep(0.3, 0.55, brightness));

    // Noise-driven dirt patches within grassy areas
    float dirtPatch = smoothstep(0.35, 0.55, patchNoise) * smoothstep(0.3, 0.5, fineNoise);
    wD += dirtPatch * 0.6 * wG;
    wG *= (1.0 - dirtPatch * 0.5);

    // Fine-scale grass/dirt mixing for ground clutter look
    float microMix = smoothstep(0.4, 0.6, microNoise);
    wD += microMix * 0.15;

    // Ensure minimum dirt presence
    wD = max(wD, 0.12);

    // Gravel in transitions and low spots
    wV = max(wV, smoothstep(0.45, 0.65, patchNoise) * 0.2);

    float groundSum = wG + wD + wV + 0.001;
    wG /= groundSum;
    wD /= groundSum;
    wV /= groundSum;

    // Rock on steep slopes
    float wR = smoothstep(0.25, 0.55, slope);
    float ground = 1.0 - wR;
    wG *= ground;
    wD *= ground;
    wV *= ground;

    // Blend material textures
    vec3 texColor = grass * wG + dirt * wD + rock * wR + gravel * wV;

    // The splatmap weights already encode biome identity (green biome → grass,
    // brown biome → dirt, etc.), so the texture blend is the primary colour.
    // Add a subtle biome hue nudge without washing out texture detail.
    vec3 albedo = texColor * mix(vec3(1.0), bc * 1.6, 0.25);
    albedo = clamp(albedo, 0.0, 1.0);

    // Per-material roughness
    float roughness = 0.80 * wG + 0.92 * wD + 0.96 * wR + 0.86 * wV;

    // Texture-derived AO
    float texLum = dot(texColor, vec3(0.299, 0.587, 0.114));
    float ao = clamp(texLum * 1.6 + 0.2, 0.0, 1.0);

    // Normal perturbation from texture luminance gradient (cheap heightfield bump)
    float eps = 0.15;
    float hC = texLum;
    float hR = dot(triplanar(u_grassTex, v_worldPos + vec3(eps,0,0), blend, s), vec3(0.3,0.6,0.1)) * wG
             + dot(triplanar(u_dirtTex,  v_worldPos + vec3(eps,0,0), blend, s*0.7), vec3(0.3,0.6,0.1)) * wD
             + dot(triplanar(u_rockTex,  v_worldPos + vec3(eps,0,0), blend, s*0.5), vec3(0.3,0.6,0.1)) * wR;
    float hU = dot(triplanar(u_grassTex, v_worldPos + vec3(0,0,eps), blend, s), vec3(0.3,0.6,0.1)) * wG
             + dot(triplanar(u_dirtTex,  v_worldPos + vec3(0,0,eps), blend, s*0.7), vec3(0.3,0.6,0.1)) * wD
             + dot(triplanar(u_rockTex,  v_worldPos + vec3(0,0,eps), blend, s*0.5), vec3(0.3,0.6,0.1)) * wR;

    // Build bump in world-space tangent frame so perturbation direction is camera-independent.
    // Mixing view-space normal with world-space offsets caused the bump to "swim" as the camera
    // moved, producing SSAO hemisphere mismatches and popping dark patches at distance.
    vec3 worldN = normalize(v_worldNormal);
    vec3 up = abs(worldN.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 worldRight   = normalize(cross(up, worldN));
    vec3 worldForward = cross(worldN, worldRight);
    vec3 bumpedWorld = normalize(worldN + worldRight * (hC - hR) * 0.4 + worldForward * (hC - hU) * 0.4);
    vec3 perturbedNormal = normalize(u_normalMatrix * bumpedWorld);

    if (u_darkBackfaces && !gl_FrontFacing) {
        albedo *= 0.05;
    }

#ifdef TERRAIN_FADE
    {
        // Write gl_FragDepth before any discard so drivers that apply early stencil
        // optimisations cannot write stencil=1 for fragments we subsequently discard
        // (which would cause the deferred lighting pass to shade empty G-buffer pixels black).
        gl_FragDepth = gl_FragCoord.z;
        const float bayer[16] = float[16](
             0.0,  8.0,  2.0, 10.0,
            12.0,  4.0, 14.0,  6.0,
             3.0, 11.0,  1.0,  9.0,
            15.0,  7.0, 13.0,  5.0
        );
        int bx = int(mod(gl_FragCoord.x, 4.0));
        int by = int(mod(gl_FragCoord.y, 4.0));
        if (u_terrainFade < bayer[by * 4 + bx] / 16.0) discard;
    }
#endif

    rt0_albedoMetallic    = vec4(albedo, 0.0);
    rt1_normalRoughnessAO = vec4(octEncode(perturbedNormal), roughness, ao);
    rt2_emissive          = vec4(vec3(0.0), gl_FragCoord.z);
}
