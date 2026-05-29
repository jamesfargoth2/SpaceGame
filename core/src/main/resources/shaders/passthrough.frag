#version 330

in vec2 v_texCoord;
uniform sampler2D u_inputTex;
out vec4 fragColor;

void main() {
    fragColor = texture(u_inputTex, v_texCoord);
}
