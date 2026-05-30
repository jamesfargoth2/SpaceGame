#version 330

#include "include/normal_encoding.glsl"

#ifdef HAS_VERTEX_COLOR
in vec4 v_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
in float v_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
in vec2 v_texCoord;
uniform sampler2D u_albedoMap;
#endif

#ifdef HAS_NORMAL_MAP
uniform sampler2D u_normalMap;
#endif

#ifdef HAS_MR_MAP
uniform sampler2D u_metallicRoughnessMap;
#endif

#ifdef HAS_EMISSIVE_MAP
uniform sampler2D u_emissiveMap;
#endif

#ifdef HAS_AO_MAP
uniform sampler2D u_aoMap;
#endif

in vec3 v_viewNormal;
in vec3 v_viewPos;

uniform vec4 u_albedoTint;
uniform float u_metallicScale;
uniform float u_roughnessScale;
uniform float u_emissiveIntensity;
uniform vec2 u_tiling;
// Terrain-only backface darkening: only set by renderTerrain().
// Darkens the inside of steep terrain only when the fragment is near camera altitude.
uniform bool u_darkBackfaces;
uniform vec3 u_cameraWorldPos;

#ifdef TERRAIN_FADE
// 0=fully transparent, 1=fully opaque. Drives screen-door dither for altitude fade.
uniform float u_terrainFade;
#endif

layout(location = 0) out vec4 rt0_albedoMetallic;
layout(location = 1) out vec4 rt1_normalRoughnessAO;
layout(location = 2) out vec4 rt2_emissive;

void main() {
    vec2 uv = vec2(0.0);
    #ifdef HAS_ALBEDO_MAP
    uv = v_texCoord * u_tiling;
    #endif

    // Albedo
    vec3 albedo = u_albedoTint.rgb;
    #ifdef HAS_VERTEX_COLOR
    albedo = v_color.rgb;
    #endif
    #ifdef HAS_ALBEDO_MAP
    albedo *= texture(u_albedoMap, uv).rgb;
    #endif

    // Metallic + Roughness
    float metallic = u_metallicScale;
    float roughness = u_roughnessScale;
    #ifdef HAS_MR_MAP
    vec4 mr = texture(u_metallicRoughnessMap, uv);
    roughness *= mr.g;
    metallic *= mr.b;
    #endif

    // Normal
    vec3 normal = normalize(v_viewNormal);
    #ifdef HAS_NORMAL_MAP
    vec3 mapNormal = texture(u_normalMap, uv).rgb * 2.0 - 1.0;
    normal = normalize(normal + mapNormal * 0.5);
    #endif

    // AO
    float ao = 1.0;
    #ifdef HAS_AO_MAP
    ao = texture(u_aoMap, uv).r;
    #endif

    // Emissive
    vec3 emissive = vec3(0.0);
    #ifdef HAS_EMISSIVE_MAP
    emissive = texture(u_emissiveMap, uv).rgb * u_emissiveIntensity;
    #endif
    #ifdef HAS_EMISSIVE_ATTRIB
    emissive = albedo * v_emissive * u_emissiveIntensity;
    #endif

    // Backface darkening: only applies when u_darkBackfaces is true (set by renderTerrain
    // when the camera clips into a steep face). All other geometry leaves it false so their
    // interior faces are not accidentally blackened.
    if (u_darkBackfaces && !gl_FrontFacing) {
        albedo = albedo * 0.05;
    }

    // Altitude-based screen-door fade for sphere terrain
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

    // Pack into G-Buffer
    rt0_albedoMetallic = vec4(albedo, metallic);
    rt1_normalRoughnessAO = vec4(octEncode(normal), roughness, ao);
    rt2_emissive = vec4(emissive, gl_FragCoord.z);
}
