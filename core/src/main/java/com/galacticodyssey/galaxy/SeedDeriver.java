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
