#version 330

#include "include/pbr_common.glsl"
#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"

uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_depth;

uniform mat4 u_invProjection;
uniform vec2 u_screenSize;
uniform vec3 u_lightPos;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform float u_lightRadius;

out vec4 fragColor;

void main() {
    vec2 texCoord = gl_FragCoord.xy / u_screenSize;

    vec4 rt0 = texture(u_rt0, texCoord);
    vec4 rt1 = texture(u_rt1, texCoord);
    float depth = texture(u_depth, texCoord).r;

    vec3 albedo = rt0.rgb;
    float metallic = rt0.a;
    vec3 N = octDecode(rt1.xy);
    float roughness = rt1.z;

    vec3 viewPos = reconstructViewPos(texCoord, depth, u_invProjection);
    vec3 V = normalize(-viewPos);
    vec3 L = u_lightPos - viewPos;
    float dist = length(L);
    L = normalize(L);

    float attenuation = 1.0 / (1.0 + (dist / u_lightRadius) * (dist / u_lightRadius));
    float falloff = clamp(1.0 - dist / u_lightRadius, 0.0, 1.0);
    attenuation *= falloff * falloff;

    vec3 Lo = evaluatePBR(N, V, L, albedo, metallic, roughness, u_lightColor, u_lightIntensity * attenuation);

    fragColor = vec4(Lo, 1.0);
}
