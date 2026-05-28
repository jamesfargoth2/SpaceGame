package com.galacticodyssey.galaxy;

public final class SeedDeriver {

    public static final long STAR_DOMAIN       = 0x6C62272E07BB0142L;
    public static final long PLANET_DOMAIN     = 0x517CC1B727220A95L;
    public static final long MOON_DOMAIN       = 0xBF58476D1CE4E5B9L;
    public static final long TERRAIN_DOMAIN    = 0x94D049BB133111EBL;
    public static final long ATMOSPHERE_DOMAIN = 0xC4CEB9FE1A85EC53L;
    public static final long BIOME_DOMAIN      = 0xD2A98B26625EEE7BL;
    public static final long STATION_DOMAIN    = 0x3C79AC492BA7B653L;
    public static final long INTERIOR_DOMAIN   = 0xE7037ED1A0B428DBL;
    public static final long FACTION_DOMAIN    = 0x4F6CDD1CB33DA28DL;
    public static final long NAME_DOMAIN       = 0x8C4F9B29D25B9E63L;
    public static final long NEBULA_DOMAIN     = 0xA2F9836E4E441529L;
    public static final long DERELICT_DOMAIN       = 0x2E8BA2E8BA2E8BA3L;
    public static final long CAVE_DOMAIN           = 0x7A6D76E9E6237015L;
    public static final long DUNGEON_DOMAIN        = 0x5D19E57F4F22A935L;
    public static final long ASTEROID_SHAPE_DOMAIN = 0x1B4E81B4E81B4E82L;
    public static final long ENCOUNTER_DOMAIN      = 0x9C49FBD688E6BF6DL;
    public static final long ECONOMY_DOMAIN        = 0x3F56B0C4FCA1AF8BL;
    public static final long CRATER_DOMAIN         = 0xAB54A98CEB1C3F47L;
    public static final long NPC_DOMAIN            = 0xF2A84C39E71B5D06L;
    public static final long CLIMATE_DOMAIN        = 0xE3A1B5C72D4F8901L;
    public static final long DRAINAGE_DOMAIN       = 0x4B7D9E2F1A6C3508L;
    public static final long TECTONIC_DOMAIN       = 0x8F2C6A4E1D5B7903L;
    public static final long EROSION_DOMAIN        = 0x5E1D3A7B9C2F4680L;
    public static final long FAUNA_DOMAIN          = 0x6A09E667F3BCC909L;

    private SeedDeriver() {}

    public static long domain(long parentSeed, long domainConstant) {
        return mix(parentSeed ^ domainConstant);
    }

    public static long starDomain(long galaxySeed) {
        return domain(galaxySeed, STAR_DOMAIN);
    }

    public static long nebulaDomain(long galaxySeed) {
        return domain(galaxySeed, NEBULA_DOMAIN);
    }

    public static long npcDomain(long parentSeed) {
        return domain(parentSeed, NPC_DOMAIN);
    }

    public static long faunaDomain(long parentSeed) {
        return domain(parentSeed, FAUNA_DOMAIN);
    }

    public static long forId(long domainSeed, long id) {
        return mix(domainSeed ^ id);
    }

    public static long forChunk(long domainSeed, int cx, int cy) {
        long h = domainSeed;
        h ^= ((long) cx) * 0x9E3779B97F4A7C15L;
        h ^= ((long) cy) * 0x6C62272E07BB0142L;
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
