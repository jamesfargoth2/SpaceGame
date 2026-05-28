package com.galacticodyssey.rendering.materials;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.galacticodyssey.rendering.shaders.ShaderCache;
import java.util.ArrayList;
import java.util.List;

public class GBufferShaderProvider {

    private final ShaderCache shaderCache;

    public GBufferShaderProvider(ShaderCache shaderCache) {
        this.shaderCache = shaderCache;
    }

    public ShaderProgram getShaderFor(MaterialComponent material, boolean hasVertexColor, boolean hasEmissiveAttrib) {
        List<String> defines = new ArrayList<>();

        if (hasVertexColor) {
            defines.add("HAS_VERTEX_COLOR");
        }
        if (hasEmissiveAttrib) {
            defines.add("HAS_EMISSIVE_ATTRIB");
        }
        if (material != null) {
            if (material.albedoMap != null) defines.add("HAS_ALBEDO_MAP");
            if (material.normalMap != null) defines.add("HAS_NORMAL_MAP");
            if (material.metallicRoughnessMap != null) defines.add("HAS_MR_MAP");
            if (material.emissiveMap != null) defines.add("HAS_EMISSIVE_MAP");
            if (material.aoMap != null) defines.add("HAS_AO_MAP");
        }

        return shaderCache.get("gbuffer.vert", "gbuffer.frag", defines.toArray(new String[0]));
    }

    public void bindMaterialUniforms(ShaderProgram shader, MaterialComponent material) {
        if (material == null) {
            shader.setUniformf("u_albedoTint", 0.8f, 0.8f, 0.8f, 1f);
            shader.setUniformf("u_metallicScale", 0f);
            shader.setUniformf("u_roughnessScale", 0.5f);
            shader.setUniformf("u_emissiveIntensity", 0f);
            shader.setUniformf("u_tiling", 1f, 1f);
            return;
        }

        shader.setUniformf("u_albedoTint", material.albedoTint);
        shader.setUniformf("u_metallicScale", material.metallicScale);
        shader.setUniformf("u_roughnessScale", material.roughnessScale);
        shader.setUniformf("u_emissiveIntensity", material.emissiveIntensity);
        shader.setUniformf("u_tiling", material.tilingX, material.tilingY);

        int texUnit = 0;
        if (material.albedoMap != null) {
            material.albedoMap.bind(texUnit);
            shader.setUniformi("u_albedoMap", texUnit++);
        }
        if (material.normalMap != null) {
            material.normalMap.bind(texUnit);
            shader.setUniformi("u_normalMap", texUnit++);
        }
        if (material.metallicRoughnessMap != null) {
            material.metallicRoughnessMap.bind(texUnit);
            shader.setUniformi("u_metallicRoughnessMap", texUnit++);
        }
        if (material.emissiveMap != null) {
            material.emissiveMap.bind(texUnit);
            shader.setUniformi("u_emissiveMap", texUnit++);
        }
        if (material.aoMap != null) {
            material.aoMap.bind(texUnit);
            shader.setUniformi("u_aoMap", texUnit);
        }
    }
}
