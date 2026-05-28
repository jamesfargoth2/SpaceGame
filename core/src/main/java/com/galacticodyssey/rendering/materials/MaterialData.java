package com.galacticodyssey.rendering.materials;

public class MaterialData {
    public String name;
    public String albedoMap;
    public String normalMap;
    public String metallicRoughnessMap;
    public String emissiveMap;
    public String aoMap;
    public float tilingX = 1f;
    public float tilingY = 1f;
    public float[] albedoTint = {1f, 1f, 1f, 1f};
    public float metallicScale = 1f;
    public float roughnessScale = 1f;
    public float emissiveIntensity = 0f;
}
