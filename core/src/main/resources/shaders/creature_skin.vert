#version 330

in vec3 a_position;
in vec3 a_normal;
in vec4 a_color;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_viewNormal;
out vec3 v_worldPos;
out vec4 v_color;
out vec3 v_objectPos;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_objectPos = a_position;
    gl_Position = u_projViewTrans * worldPos;
    v_viewNormal = normalize(u_normalMatrix * a_normal);
    v_color = a_color;
}
