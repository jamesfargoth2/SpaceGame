#version 330

in vec3 a_position;
in vec3 a_normal;

#ifdef HAS_VERTEX_COLOR
in vec4 a_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
in float a_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
in vec2 a_texCoord0;
#endif

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_viewNormal;
out vec3 v_viewPos;

#ifdef HAS_VERTEX_COLOR
out vec4 v_color;
#endif

#ifdef HAS_EMISSIVE_ATTRIB
out float v_emissive;
#endif

#ifdef HAS_ALBEDO_MAP
out vec2 v_texCoord;
#endif

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    vec4 viewPos4 = u_projViewTrans * vec4(a_position, 1.0);
    v_viewPos = worldPos.xyz;
    v_viewNormal = normalize(u_normalMatrix * a_normal);

    #ifdef HAS_VERTEX_COLOR
    v_color = a_color;
    #endif

    #ifdef HAS_EMISSIVE_ATTRIB
    v_emissive = a_emissive;
    #endif

    #ifdef HAS_ALBEDO_MAP
    v_texCoord = a_texCoord0;
    #endif

    gl_Position = u_projViewTrans * worldPos;
}
