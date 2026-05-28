#version 330
in vec3 a_position;

uniform mat4 u_projViewTrans;
uniform vec3 u_lightPos;
uniform float u_lightRadius;

void main() {
    vec3 worldPos = a_position * u_lightRadius + u_lightPos;
    gl_Position = u_projViewTrans * vec4(worldPos, 1.0);
}
