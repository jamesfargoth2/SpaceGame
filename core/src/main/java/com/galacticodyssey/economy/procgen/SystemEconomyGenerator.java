package com.galacticodyssey.economy.procgen;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a {@link SystemEconomy} for a star system based on its seed,
 * owning faction ethos, and distance from the galactic core.
 */
public final class SystemEconomyGenerator {

    /** All commodity IDs that the generator considers. */
    private static final List<String> ALL_COMMODITIES = Arrays.asList(
            "iron_ore", "copper", "silicon", "carbon", "water", "food_rations",
            "titanium", "lithium_cells", "tungsten_alloy", "medical_supplies",
            "synthetic_textiles", "manufactured_parts", "iridium_ingots", "neutronium",
            "dark_crystals", "military_electronics", "luxury_goods", "zero_point_cells",
            "quantum_foam", "void_essence", "salvaged_components", "bio_polymers",
            "xeno_tech_fragments", "psionic_resonators"
    );

    /** Galactic base prices mirroring commodities.json. */
    private static final Map<String, Float> GALACTIC_BASE_PRICES = new HashMap<>();
    static {
        GALACTIC_BASE_PRICES.put("iron_ore", 15f);
        GALACTIC_BASE_PRICES.put("copper", 12f);
        GALACTIC_BASE_PRICES.put("silicon", 18f);
        GALACTIC_BASE_PRICES.put("carbon", 8f);
        GALACTIC_BASE_PRICES.put("water", 10f);
        GALACTIC_BASE_PRICES.put("food_rations", 25f);
        GALACTIC_BASE_PRICES.put("titanium", 85f);
        GALACTIC_BASE_PRICES.put("lithium_cells", 120f);
        GALACTIC_BASE_PRICES.put("tungsten_alloy", 150f);
        GALACTIC_BASE_PRICES.put("medical_supplies", 95f);
        GALACTIC_BASE_PRICES.put("synthetic_textiles", 75f);
        GALACTIC_BASE_PRICES.put("manufactured_parts", 110f);
        GALACTIC_BASE_PRICES.put("iridium_ingots", 750f);
        GALACTIC_BASE_PRICES.put("neutronium", 1200f);
        GALACTIC_BASE_PRICES.put("dark_crystals", 900f);
        GALACTIC_BASE_PRICES.put("military_electronics", 1500f);
        GALACTIC_BASE_PRICES.put("luxury_goods", 600f);
        GALACTIC_BASE_PRICES.put("zero_point_cells", 8000f);
        GALACTIC_BASE_PRICES.put("quantum_foam", 12000f);
        GALACTIC_BASE_PRICES.put("void_essence", 6000f);
        GALACTIC_BASE_PRICES.put("salvaged_components", 5000f);
        GALACTIC_BASE_PRICES.put("bio_polymers", 3500f);
        GALACTIC_BASE_PRICES.put("xeno_tech_fragments", 7500f);
        GALACTIC_BASE_PRICES.put("psionic_resonators", 10000f);
    }

    private SystemEconomyGenerator() {}

    /**
     * Generates a complete system economy.
     *
     * @param systemSeed      deterministic seed for this star system
     * @param factionEthos    owning faction ethos string (e.g. "MILITARIST"), or null for unclaimed
     * @param distFromCoreLY  normalised distance from galactic core, 0 = core, 1 = outer rim
     * @return fully populated SystemEconomy
     */
    public static SystemEconomy generate(long systemSeed, String factionEthos, float distFromCoreLY) {
        long econSeed = SeedDeriver.domain(systemSeed, SeedDeriver.STAR_DOMAIN);
        Random rng = new Random(econSeed);

        EconomyType type = pickEconomyType(rng, factionEthos, distFromCoreLY);

        float wealthLevel = clamp01(RngUtil.range(rng, 0.2f, 0.9f) - distFromCoreLY * 0.2f);
        float techLevel = clamp01(RngUtil.range(rng, 0.1f, 0.8f) + (type == EconomyType.RESEARCH_STATION ? 0.3f : 0f));
        float volatility = RngUtil.range(rng, 0.05f, 0.30f);

        Map<String, Float> supply = new HashMap<>();
        Map<String, Float> demand = new HashMap<>();
        buildSupplyDemandProfiles(type, supply, demand);
        applyNoise(rng, supply);
        applyNoise(rng, demand);

        Map<String, Float> prices = computePrices(supply, demand, wealthLevel, volatility, rng);

        Set<String> contraband = buildContraband(factionEthos);
        boolean hasBlackMarket = determineBlackMarket(type, factionEthos, rng);

        return new SystemEconomy(systemSeed, type, supply, demand, prices,
                contraband, wealthLevel, techLevel, hasBlackMarket, volatility);
    }

    // ---- economy type selection ----

    private static EconomyType pickEconomyType(Random rng, String factionEthos, float distFromCoreLY) {
        float roll = rng.nextFloat();

        if ("MILITARIST".equals(factionEthos)) {
            if (roll < 0.40f) return EconomyType.MILITARY_BASE;
            if (roll < 0.70f) return EconomyType.INDUSTRIAL;
            return pickGeneric(rng, distFromCoreLY);
        }
        if ("CORPORATE".equals(factionEthos)) {
            if (roll < 0.40f) return EconomyType.TRADING_HUB;
            if (roll < 0.70f) return EconomyType.INDUSTRIAL;
            return pickGeneric(rng, distFromCoreLY);
        }
        if ("PIRATE_SYNDICATE".equals(factionEthos)) {
            if (roll < 0.60f) return EconomyType.PIRATE_OUTPOST;
            return pickGeneric(rng, distFromCoreLY);
        }

        return pickGeneric(rng, distFromCoreLY);
    }

    private static EconomyType pickGeneric(Random rng, float distFromCoreLY) {
        // Rim bias towards mining
        float miningWeight = 1.0f + distFromCoreLY * 2.0f;
        float[] weights = {
                miningWeight,   // MINING_COLONY
                1.0f,           // AGRICULTURAL
                1.0f,           // INDUSTRIAL
                1.0f,           // TRADING_HUB
                0.5f,           // MILITARY_BASE
                0.5f,           // RESEARCH_STATION
                0.2f            // PIRATE_OUTPOST
        };
        EconomyType[] types = EconomyType.values();
        float total = 0f;
        for (float w : weights) total += w;

        float pick = rng.nextFloat() * total;
        float cumulative = 0f;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (pick < cumulative) return types[i];
        }
        return types[types.length - 1];
    }

    // ---- supply / demand profiles ----

    private static void buildSupplyDemandProfiles(EconomyType type,
                                                   Map<String, Float> supply,
                                                   Map<String, Float> demand) {
        // Set baseline at 0.3 for everything
        for (String id : ALL_COMMODITIES) {
            supply.put(id, 0.3f);
            demand.put(id, 0.3f);
        }

        switch (type) {
            case MINING_COLONY:
                setHigh(supply, "iron_ore", "copper", "silicon", "carbon", "titanium");
                setHigh(demand, "food_rations", "manufactured_parts", "medical_supplies");
                break;
            case AGRICULTURAL:
                setHigh(supply, "food_rations", "water", "bio_polymers");
                setHigh(demand, "manufactured_parts", "medical_supplies", "synthetic_textiles");
                break;
            case INDUSTRIAL:
                setHigh(supply, "manufactured_parts", "tungsten_alloy", "synthetic_textiles");
                setHigh(demand, "iron_ore", "copper", "silicon", "carbon");
                break;
            case TRADING_HUB:
                // Balanced, slightly above average for everything
                for (String id : ALL_COMMODITIES) {
                    supply.put(id, 0.55f);
                    demand.put(id, 0.55f);
                }
                break;
            case MILITARY_BASE:
                setHigh(supply, "military_electronics", "manufactured_parts");
                setHigh(demand, "food_rations", "water", "lithium_cells", "titanium");
                break;
            case RESEARCH_STATION:
                setHigh(supply, "xeno_tech_fragments", "quantum_foam");
                // High demand for everything — research stations need supplies
                for (String id : ALL_COMMODITIES) {
                    demand.put(id, Math.max(demand.get(id), 0.6f));
                }
                break;
            case PIRATE_OUTPOST:
                setHigh(supply, "salvaged_components", "luxury_goods");
                setHigh(demand, "military_electronics", "medical_supplies", "lithium_cells");
                break;
        }
    }

    private static void setHigh(Map<String, Float> map, String... ids) {
        for (String id : ids) {
            map.put(id, 0.85f);
        }
    }

    private static void applyNoise(Random rng, Map<String, Float> map) {
        // Use sorted keys for deterministic iteration across JVM implementations
        List<String> keys = new ArrayList<>(map.keySet());
        java.util.Collections.sort(keys);
        for (String key : keys) {
            float noise = RngUtil.range(rng, -0.15f, 0.15f);
            map.put(key, clamp01(map.get(key) + noise));
        }
    }

    // ---- pricing ----

    private static Map<String, Float> computePrices(Map<String, Float> supply,
                                                     Map<String, Float> demand,
                                                     float wealthLevel,
                                                     float volatility,
                                                     Random rng) {
        float wealthFactor = 0.7f + wealthLevel * 0.6f; // 0.7 to 1.3
        Map<String, Float> prices = new HashMap<>();
        for (String id : ALL_COMMODITIES) {
            float s = Math.max(supply.getOrDefault(id, 0.3f), 0.01f);
            float d = demand.getOrDefault(id, 0.3f);
            float galacticBase = GALACTIC_BASE_PRICES.getOrDefault(id, 100f);
            float noise = RngUtil.range(rng, -1f, 1f);
            float price = galacticBase * (d / s) * wealthFactor * (1f + volatility * noise);
            prices.put(id, Math.max(1f, price));
        }
        return prices;
    }

    // ---- contraband ----

    private static Set<String> buildContraband(String factionEthos) {
        Set<String> contraband = new HashSet<>();
        // Slaves are always illegal (not a commodity in the system, but mark it)
        contraband.add("slaves");

        if ("MILITARIST".equals(factionEthos)) {
            contraband.add("narcotics");
            contraband.add("salvaged_components"); // stolen goods equivalent
        } else if ("CORPORATE".equals(factionEthos)) {
            contraband.add("salvaged_components"); // stolen goods
        } else if ("PIRATE_SYNDICATE".equals(factionEthos)) {
            // Pirates don't ban much — sometimes alien tech
            // but nothing from the regular list is illegal
            contraband.remove("slaves"); // pirates don't care
            // Spec says sometimes alien tech, but since contraband is deterministic
            // per faction we leave it empty for pirates
            contraband.clear();
        } else if ("ISOLATIONIST".equals(factionEthos)) {
            contraband.add("military_electronics");
            contraband.add("xeno_tech_fragments");
            contraband.add("psionic_resonators");
        }

        return contraband;
    }

    // ---- black market ----

    private static boolean determineBlackMarket(EconomyType type, String factionEthos, Random rng) {
        if (type == EconomyType.PIRATE_OUTPOST) return true;
        if ("ISOLATIONIST".equals(factionEthos)) return rng.nextFloat() < 0.20f;
        return rng.nextFloat() < 0.10f;
    }

    // ---- utility ----

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
