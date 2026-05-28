#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_direction;
uniform vec2 u_texelSize;

out float fragColor;

void main() {
    float result = 0.0;
    float weightSum = 0.0;
    float centerVal = texture(u_inputTex, v_texCoord).r;

    for (int i = -2; i <= 2; i++) {
        vec2 offset = u_direction * float(i) * u_texelSize;
        float sample_ = texture(u_inputTex, v_texCoord + offset).r;
        float weight = 1.0 / (1.0 + abs(sample_ - centerVal) * 10.0);
        result += sample_ * weight;
        weightSum += weight;
    }

    fragColor = result / weightSum;
}
