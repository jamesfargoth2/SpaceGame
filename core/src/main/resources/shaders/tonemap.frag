#version 330

in vec2 v_texCoord;

uniform sampler2D u_hdrTex;
uniform float u_exposure;

out vec4 fragColor;

vec3 acesTonemap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 hdr = texture(u_hdrTex, v_texCoord).rgb;
    hdr *= u_exposure;
    vec3 ldr = acesTonemap(hdr);
    ldr = pow(ldr, vec3(1.0 / 2.2));
    fragColor = vec4(ldr, 1.0);
}
