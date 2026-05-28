package com.galacticodyssey.vfx.data;

import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public class ParticleEffectDefinition {
    public String id;
    /** "BILLBOARD" (default) or "MESH" */
    public String type = "BILLBOARD";
    /** Atlas region name for BILLBOARD type (e.g. "smoke", "flash"). */
    public String sprite = "smoke";
    /** Mesh asset ID for MESH type (e.g. "spark_line"). */
    public String mesh = "";
    public int maxParticles = 16;
    public float emitRate;
    public int burstCount;
    /** If true, emit all particles once on spawn and stop (MESH type). */
    public boolean emitOnce = false;
    public float lifetimeMin = 0.5f, lifetimeMax = 1.0f;
    public float speedMin = 1f, speedMax = 5f;
    public float spread = 30f;
    public float sizeMin = 0.1f, sizeMax = 0.3f;
    public float sizeEnd;
    public String color = "#FFFFFF";
    public String colorEnd = "#FFFFFF";
    /** Legacy file path — kept for backward compatibility; prefer sprite. */
    public String texture = "particles/default.png";
    public BlendMode blendMode = BlendMode.ADDITIVE;
    public float gravity;
    /** Elasticity coefficient for MESH particles bouncing off the floor (0=dead stop, 1=elastic). */
    public float bounce = 0.3f;
    public float duration = -1f;
}
