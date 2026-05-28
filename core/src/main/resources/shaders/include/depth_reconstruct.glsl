// Reconstruct view-space position from depth buffer
vec3 reconstructViewPos(vec2 texCoord, float depth, mat4 invProjection) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = invProjection * clipPos;
    return viewPos.xyz / viewPos.w;
}
