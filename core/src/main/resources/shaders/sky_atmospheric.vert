attribute vec2 a_position;
uniform mat4 u_invViewProj;
uniform vec3 u_cameraPos;
varying vec3 v_rayDir;
void main() {
    gl_Position = vec4(a_position, 0.9999, 1.0);
    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);
    v_rayDir = farPoint.xyz / farPoint.w - u_cameraPos;
}
