#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;
uniform float u_intensity;

out vec4 fragColor;

void main() {
    vec3 color = vec3(0.0);
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0, -1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0, -1.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0, -1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  0.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord).rgb * 4.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  0.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  1.0)).rgb * 1.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0,  1.0)).rgb * 2.0;
    color += texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  1.0)).rgb * 1.0;
    color /= 16.0;

    fragColor = vec4(color * u_intensity, 1.0);
}
