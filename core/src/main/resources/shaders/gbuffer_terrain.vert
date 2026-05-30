#version 330

in vec3 a_position;
in vec3 a_normal;
in vec4 a_color;
in vec2 a_texCoord0;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_worldPos;
out vec3 v_worldNormal;
out vec3 v_viewNormal;
out vec4 v_color;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_worldNormal = normalize(mat3(u_worldTrans) * a_normal);
    v_viewNormal = normalize(u_normalMatrix * a_normal);
    v_color = a_color;
    gl_Position = u_projViewTrans * worldPos;
}
