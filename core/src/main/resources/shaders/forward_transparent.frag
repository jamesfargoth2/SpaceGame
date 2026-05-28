#version 330

#include "include/pbr_common.glsl"

in vec3 v_worldPos;
in vec3 v_normal;
in vec2 v_texCoord;

uniform vec4 u_albedoTint;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_cameraPos;
uniform float u_alpha;

out vec4 fragColor;

void main() {
    vec3 N = normalize(v_normal);
    vec3 V = normalize(u_cameraPos - v_worldPos);
    vec3 L = normalize(-u_lightDir);

    vec3 Lo = evaluatePBR(N, V, L, u_albedoTint.rgb, 0.0, 0.5, u_lightColor, u_lightIntensity);
    vec3 ambient = u_albedoTint.rgb * 0.1;

    fragColor = vec4(Lo + ambient, u_alpha);
}
