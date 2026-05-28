#version 330

#include "include/pbr_common.glsl"
#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"

in vec2 v_texCoord;

uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_rt2;
uniform sampler2D u_ssao;

uniform mat4 u_invProjection;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_ambientColor;
uniform float u_ambientIntensity;

out vec4 fragColor;

void main() {
    vec4 rt0 = texture(u_rt0, v_texCoord);
    vec4 rt1 = texture(u_rt1, v_texCoord);
    vec4 rt2 = texture(u_rt2, v_texCoord);
    float depth = rt2.a;
    float ssao = texture(u_ssao, v_texCoord).r;

    vec3 albedo = rt0.rgb;
    float metallic = rt0.a;
    vec3 N = octDecode(rt1.xy);
    float roughness = rt1.z;
    float ao = rt1.w * ssao;
    vec3 emissive = rt2.rgb;

    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 V = normalize(-viewPos);
    vec3 L = normalize(-u_lightDir);

    vec3 Lo = evaluatePBR(N, V, L, albedo, metallic, roughness, u_lightColor, u_lightIntensity);

    vec3 ambient = u_ambientColor * u_ambientIntensity * albedo * ao;

    fragColor = vec4(Lo + ambient + emissive, 1.0);
}
