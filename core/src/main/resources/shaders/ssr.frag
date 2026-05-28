#version 330

#include "include/normal_encoding.glsl"
#include "include/depth_reconstruct.glsl"
#include "include/pbr_common.glsl"

in vec2 v_texCoord;

uniform sampler2D u_hdrTex;
uniform sampler2D u_rt0;
uniform sampler2D u_rt1;
uniform sampler2D u_depthTex;

uniform mat4 u_projection;
uniform mat4 u_invProjection;
uniform vec2 u_screenSize;
uniform float u_maxDistance;
uniform float u_thickness;
uniform int u_maxSteps;

out vec4 fragColor;

void main() {
    vec4 rt0 = texture(u_rt0, v_texCoord);
    vec4 rt1 = texture(u_rt1, v_texCoord);
    float metallic = rt0.a;
    float roughness = rt1.z;

    if (roughness > 0.7 || metallic < 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    float depth = texture(u_depthTex, v_texCoord).r;
    vec3 viewPos = reconstructViewPos(v_texCoord, depth, u_invProjection);
    vec3 N = octDecode(rt1.xy);
    vec3 V = normalize(-viewPos);
    vec3 R = reflect(-V, N);

    vec3 startPos = viewPos;
    vec3 endPos = viewPos + R * u_maxDistance;

    vec4 startClip = u_projection * vec4(startPos, 1.0);
    vec4 endClip = u_projection * vec4(endPos, 1.0);
    vec2 startScreen = (startClip.xy / startClip.w) * 0.5 + 0.5;
    vec2 endScreen = (endClip.xy / endClip.w) * 0.5 + 0.5;

    vec2 delta = endScreen - startScreen;
    float stepSize = 1.0 / float(u_maxSteps);

    vec2 hitUV = vec2(0.0);
    bool hit = false;

    for (int i = 1; i <= u_maxSteps; i++) {
        float t = float(i) * stepSize;
        vec2 sampleUV = startScreen + delta * t;

        if (sampleUV.x < 0.0 || sampleUV.x > 1.0 || sampleUV.y < 0.0 || sampleUV.y > 1.0) break;

        float sampleDepth = texture(u_depthTex, sampleUV).r;
        vec3 sampleViewPos = reconstructViewPos(sampleUV, sampleDepth, u_invProjection);
        vec3 rayPos = startPos + R * u_maxDistance * t;

        if (rayPos.z < sampleViewPos.z && rayPos.z > sampleViewPos.z - u_thickness) {
            hitUV = sampleUV;
            hit = true;
            break;
        }
    }

    if (!hit) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 reflectedColor = texture(u_hdrTex, hitUV).rgb;

    float edgeFade = 1.0;
    vec2 edgeDist = abs(hitUV - 0.5) * 2.0;
    edgeFade *= 1.0 - smoothstep(0.8, 1.0, edgeDist.x);
    edgeFade *= 1.0 - smoothstep(0.8, 1.0, edgeDist.y);

    float dirFade = clamp(dot(V, R) + 0.5, 0.0, 1.0);

    vec3 F0 = mix(vec3(0.04), rt0.rgb, metallic);
    float VdotN = max(dot(V, N), 0.0);
    vec3 fresnel = fresnelSchlick(VdotN, F0);

    float confidence = edgeFade * dirFade * (1.0 - roughness);

    fragColor = vec4(reflectedColor * fresnel * confidence, confidence);
}
