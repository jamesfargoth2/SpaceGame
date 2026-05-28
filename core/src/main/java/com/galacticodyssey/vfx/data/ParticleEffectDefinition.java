package com.galacticodyssey.vfx.data;

import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public class ParticleEffectDefinition {
    public String id;
    public String type = "BILLBOARD";
    public String sprite = "smoke";
    public String mesh = "";
    public boolean emitOnce = false;
    public float bounce = 0.3f;
    public int maxParticles = 16;
    public float emitRate;
    public int burstCount;
    public float lifetimeMin = 0.5f, lifetimeMax = 1.0f;
    public float speedMin = 1f, speedMax = 5f;
    public float spread = 30f;
    public float sizeMin = 0.1f, sizeMax = 0.3f;
    public float sizeEnd;
    public String color = "#FFFFFF";
    public String colorEnd = "#FFFFFF";
    public String texture = "particles/default.png";
    public BlendMode blendMode = BlendMode.ADDITIVE;
    public float gravity;
    public float duration = -1f;
}
