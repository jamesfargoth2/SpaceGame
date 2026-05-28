#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;
uniform float u_threshold;
uniform float u_softKnee;
uniform bool u_applyThreshold;

out vec4 fragColor;

void main() {
    vec3 A = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0, -1.0)).rgb;
    vec3 B = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0, -1.0)).rgb;
    vec3 C = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0, -1.0)).rgb;
    vec3 D = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-0.5, -0.5)).rgb;
    vec3 E = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.5, -0.5)).rgb;
    vec3 F = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  0.0)).rgb;
    vec3 G = texture(u_inputTex, v_texCoord).rgb;
    vec3 H = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  0.0)).rgb;
    vec3 I = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-0.5,  0.5)).rgb;
    vec3 J = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.5,  0.5)).rgb;
    vec3 K = texture(u_inputTex, v_texCoord + u_texelSize * vec2(-1.0,  1.0)).rgb;
    vec3 L = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 0.0,  1.0)).rgb;
    vec3 M = texture(u_inputTex, v_texCoord + u_texelSize * vec2( 1.0,  1.0)).rgb;

    vec3 color = (D + E + I + J) * 0.5 * 0.25;
    color += (A + B + F + G) * 0.125 * 0.25;
    color += (B + C + G + H) * 0.125 * 0.25;
    color += (F + G + K + L) * 0.125 * 0.25;
    color += (G + H + L + M) * 0.125 * 0.25;

    if (u_applyThreshold) {
        float brightness = max(color.r, max(color.g, color.b));
        float knee = u_threshold * u_softKnee;
        float soft = brightness - u_threshold + knee;
        soft = clamp(soft, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 0.0001);
        float contrib = max(soft, brightness - u_threshold) / max(brightness, 0.0001);
        color *= contrib;
    }

    fragColor = vec4(color, 1.0);
}
