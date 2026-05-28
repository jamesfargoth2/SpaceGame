#version 330

in vec2 v_texCoord;

uniform sampler2D u_inputTex;
uniform vec2 u_texelSize;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 rgbM = texture(u_inputTex, v_texCoord).rgb;
    vec3 rgbN = texture(u_inputTex, v_texCoord + vec2( 0.0, -1.0) * u_texelSize).rgb;
    vec3 rgbS = texture(u_inputTex, v_texCoord + vec2( 0.0,  1.0) * u_texelSize).rgb;
    vec3 rgbE = texture(u_inputTex, v_texCoord + vec2( 1.0,  0.0) * u_texelSize).rgb;
    vec3 rgbW = texture(u_inputTex, v_texCoord + vec2(-1.0,  0.0) * u_texelSize).rgb;

    float lumaM = luma(rgbM);
    float lumaN = luma(rgbN);
    float lumaS = luma(rgbS);
    float lumaE = luma(rgbE);
    float lumaW = luma(rgbW);

    float lumaMin = min(lumaM, min(min(lumaN, lumaS), min(lumaE, lumaW)));
    float lumaMax = max(lumaM, max(max(lumaN, lumaS), max(lumaE, lumaW)));
    float lumaRange = lumaMax - lumaMin;

    if (lumaRange < max(0.0312, lumaMax * 0.166)) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    vec3 rgbNW = texture(u_inputTex, v_texCoord + vec2(-1.0, -1.0) * u_texelSize).rgb;
    vec3 rgbNE = texture(u_inputTex, v_texCoord + vec2( 1.0, -1.0) * u_texelSize).rgb;
    vec3 rgbSW = texture(u_inputTex, v_texCoord + vec2(-1.0,  1.0) * u_texelSize).rgb;
    vec3 rgbSE = texture(u_inputTex, v_texCoord + vec2( 1.0,  1.0) * u_texelSize).rgb;

    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);

    float edgeH = abs(-2.0 * lumaW + lumaNW + lumaSW) + abs(-2.0 * lumaM + lumaN + lumaS) * 2.0 + abs(-2.0 * lumaE + lumaNE + lumaSE);
    float edgeV = abs(-2.0 * lumaN + lumaNW + lumaNE) + abs(-2.0 * lumaM + lumaW + lumaE) * 2.0 + abs(-2.0 * lumaS + lumaSW + lumaSE);
    bool isHorizontal = edgeH >= edgeV;

    float stepLength = isHorizontal ? u_texelSize.y : u_texelSize.x;
    float lumaP = isHorizontal ? lumaS : lumaE;
    float lumaN2 = isHorizontal ? lumaN : lumaW;

    float gradientP = abs(lumaP - lumaM);
    float gradientN = abs(lumaN2 - lumaM);

    if (gradientN > gradientP) stepLength = -stepLength;

    vec2 offset = isHorizontal ? vec2(0.0, stepLength * 0.5) : vec2(stepLength * 0.5, 0.0);
    vec3 rgbF = texture(u_inputTex, v_texCoord + offset).rgb;

    float lumaF = luma(rgbF);
    float subpixelFactor = clamp(abs(lumaF - lumaM) / lumaRange, 0.0, 1.0);
    subpixelFactor = smoothstep(0.0, 1.0, subpixelFactor);

    fragColor = vec4(mix(rgbM, rgbF, subpixelFactor * 0.75), 1.0);
}
