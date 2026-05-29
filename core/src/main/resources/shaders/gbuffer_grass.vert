#version 330

in vec3 a_position;   // base blade vertex (unit height 1.0, centred at origin base)
in vec3 a_normal;

in vec3 i_offset;     // per-instance world position (base of tuft)
in vec4 i_params;     // scaleXZ, scaleY, rotationY, windPhase
in vec3 i_color;

uniform mat4 u_projViewTrans;
uniform mat3 u_normalMatrix;   // inverse-transpose of the view matrix (as renderTerrain sets)
uniform float u_time;
uniform vec3 u_camPos;
uniform float u_fadeRadius;
uniform float u_fadeBand;
uniform float u_windAmp;
uniform float u_windFreq;

out vec3 v_viewPos;
out vec3 v_viewNormal;
out vec4 v_color;

void main() {
    float scaleXZ = i_params.x;
    float scaleY  = i_params.y;
    float rot     = i_params.z;
    float phase   = i_params.w;

    // distance fade: shrink height to zero near the radius edge
    float dist = distance(u_camPos, i_offset);
    float fade = clamp((u_fadeRadius - dist) / max(u_fadeBand, 0.001), 0.0, 1.0);
    scaleY *= fade;

    // yaw rotation around Y
    float c = cos(rot), s = sin(rot);
    vec3 p = a_position;
    vec3 scaled = vec3(p.x * scaleXZ, p.y * scaleY, p.z * scaleXZ);
    vec3 rotated = vec3(scaled.x * c + scaled.z * s, scaled.y, -scaled.x * s + scaled.z * c);
    vec3 worldPos = i_offset + rotated;

    // wind: displace tips (height factor squared so base stays planted)
    float hf = a_position.y; // 0 at base, 1 at tip (unit blade)
    float sway = u_windAmp * sin(u_time * u_windFreq + phase + i_offset.x * 0.15) * hf * hf * scaleY;
    worldPos.x += sway;
    worldPos.z += sway * 0.5;

    // rotate the normal by the same yaw, then to view space
    vec3 n = a_normal;
    vec3 nRot = vec3(n.x * c + n.z * s, n.y, -n.x * s + n.z * c);
    v_viewNormal = normalize(u_normalMatrix * nRot);

    v_viewPos = worldPos;
    v_color = vec4(i_color, 1.0);
    gl_Position = u_projViewTrans * vec4(worldPos, 1.0);
}
