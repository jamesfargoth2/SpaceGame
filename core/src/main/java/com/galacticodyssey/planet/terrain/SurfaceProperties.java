package com.galacticodyssey.planet.terrain;

public class SurfaceProperties {
    public SurfaceMaterial material;
    public float staticFriction;
    public float kineticFriction;
    public float deformationDepth;
    public float compressiveStrength;
    public float thermalConductivity;
    public float temperature;
    public float dustSuspendThreshold;

    public static SurfaceProperties forMaterial(SurfaceMaterial m) {
        SurfaceProperties p = new SurfaceProperties();
        p.material = m;
        p.thermalConductivity = 1.0f;
        p.temperature = 290f;
        switch (m) {
            case LOOSE_REGOLITH:
                p.staticFriction      = 0.35f;
                p.kineticFriction     = 0.25f;
                p.deformationDepth    = 0.002f;
                p.compressiveStrength = 1200f;
                p.dustSuspendThreshold = 2f;
                break;
            case ICE:
                p.staticFriction      = 0.05f;
                p.kineticFriction     = 0.03f;
                p.deformationDepth    = 0.0001f;
                p.compressiveStrength = 5e6f;
                p.dustSuspendThreshold = Float.MAX_VALUE;
                break;
            case BARE_ROCK:
                p.staticFriction      = 0.7f;
                p.kineticFriction     = 0.6f;
                p.deformationDepth    = 0f;
                p.compressiveStrength = Float.MAX_VALUE;
                p.dustSuspendThreshold = Float.MAX_VALUE;
                break;
            case COMPACTED_SOIL:
                p.staticFriction      = 0.5f;
                p.kineticFriction     = 0.4f;
                p.deformationDepth    = 0.001f;
                p.compressiveStrength = 3000f;
                p.dustSuspendThreshold = 5f;
                break;
            case DEEP_REGOLITH:
                p.staticFriction      = 0.2f;
                p.kineticFriction     = 0.15f;
                p.deformationDepth    = 0.005f;
                p.compressiveStrength = 500f;
                p.dustSuspendThreshold = 1f;
                break;
            case LAVA_CRUST:
                p.staticFriction      = 0.6f;
                p.kineticFriction     = 0.5f;
                p.deformationDepth    = 0.003f;
                p.compressiveStrength = 2000f;
                p.dustSuspendThreshold = Float.MAX_VALUE;
                p.temperature         = 800f;
                p.thermalConductivity = 2.0f;
                break;
            case SAND_DUNE:
                p.staticFriction      = 0.3f;
                p.kineticFriction     = 0.2f;
                p.deformationDepth    = 0.004f;
                p.compressiveStrength = 800f;
                p.dustSuspendThreshold = 3f;
                break;
        }
        return p;
    }
}
