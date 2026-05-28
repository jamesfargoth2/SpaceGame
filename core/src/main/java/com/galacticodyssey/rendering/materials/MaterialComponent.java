package com.galacticodyssey.rendering.materials;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;

public class MaterialComponent implements Component {
    public Texture albedoMap;
    public Texture normalMap;
    public Texture metallicRoughnessMap;
    public Texture emissiveMap;
    public Texture aoMap;
    public float tilingX = 1f;
    public float tilingY = 1f;
    public final Color albedoTint = new Color(Color.WHITE);
    public float metallicScale = 1f;
    public float roughnessScale = 1f;
    public float emissiveIntensity = 0f;
}
