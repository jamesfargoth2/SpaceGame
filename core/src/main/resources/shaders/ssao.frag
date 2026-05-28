#version 330

#include "include/depth_reconstruct.glsl"
#include "include/normal_encoding.glsl"

in vec2 v_texCoord;

uniform sampler2D u_normalTex;
uniform sampler2D u_depthTex;
uniform sampler2D u_noiseTex;

uniform vec3 u_samples[32];
uniform int u_sampleCount;
uniform mat4 u_projection;
uniform mat4 u_invProjection;
uniform vec2 u_noiseScale;
uniform float u_radius;
uniform float u_bias;

out float fragOcclusion;

void main() {
    float depth = texture(u_depthTex, v_texCoord).a;
    if (depth >= 1.0) { fragOcclusion = 1.0; return; }

    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 normal = octDecode(texture(u_normalTex, v_texCoord).xy);
    vec3 randomVec = normalize(texture(u_noiseTex, v_texCoord * u_noiseScale).xyz * 2.0 - 1.0);

    vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    mat3 TBN = mat3(tangent, bitangent, normal);

    float occlusion = 0.0;
    for (int i = 0; i < u_sampleCount; i++) {
        vec3 samplePos = viewPos + TBN * u_samples[i] * u_radius;

        vec4 offset = u_projection * vec4(samplePos, 1.0);
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5;

        float sampleDepth = texture(u_depthTex, offset.xy).a;
        vec3 sampleViewPos = reconstructViewPos(offset.xy, sampleDepth, u_invProjection);

        float rangeCheck = smoothstep(0.0, 1.0, u_radius / abs(viewPos.z - sampleViewPos.z));
        occlusion += (sampleViewPos.z >= samplePos.z + u_bias ? 1.0 : 0.0) * rangeCheck;
    }

    fragOcclusion = 1.0 - (occlusion / float(u_sampleCount));
}
