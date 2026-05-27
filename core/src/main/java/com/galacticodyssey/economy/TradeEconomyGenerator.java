package com.galacticodyssey.economy;

import com.galacticodyssey.economy.data.*;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class TradeEconomyGenerator {

    private static final String[] COMMON_COMMODITIES = {
        "iron_ore", "copper", "silicon", "carbon", "water", "food_rations"
    };
    private static final String[] UNCOMMON_COMMODITIES = {
        "titanium", "lithium_cells", "tungsten_alloy", "medical_supplies", "synthetic_textiles", "manufactured_parts"
    };
    private static final String[] RARE_COMMODITIES = {
        "iridium_ingots", "neutronium", "dark_crystals", "military_electronics", "luxury_goods"
    };
    private static final String[] EXOTIC_COMMODITIES = {
        "zero_point_cells", "quantum_foam", "void_essence", "salvaged_components"
    };

    public LocalEconomy generate(long seed, IndustryType industry, int population, int techLevel) {
        long econSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ECONOMY_DOMAIN), 0);
        Random rng = new Random(econSeed);

        Map<String, Float> priceModifiers = new HashMap<>();
        Map<String, SupplyLevel> supplyLevels = new HashMap<>();
        List<String> specializations = new ArrayList<>();

        applyIndustryPricing(industry, rng, priceModifiers, supplyLevels, specializations);

        if (techLevel < 3) {
            for (String exotic : EXOTIC_COMMODITIES) {
                supplyLevels.put(exotic, SupplyLevel.UNAVAILABLE);
            }
        }
        if (techLevel < 2) {
            for (String rare : RARE_COMMODITIES) {
                supplyLevels.put(rare, SupplyLevel.SCARCE);
                priceModifiers.put(rare, priceModifiers.getOrDefault(rare, 1f) * 1.5f);
            }
        }

        if (population < 1000) {
            for (String uncommon : UNCOMMON_COMMODITIES) {
                if (rng.nextFloat() < 0.4f) {
                    supplyLevels.put(uncommon, SupplyLevel.UNAVAILABLE);
                }
            }
        }

        boolean blackMarket = rng.nextFloat() < blackMarketChance(industry, population, techLevel);

        float taxRate = baseTaxForIndustry(industry) + RngUtil.range(rng, -0.02f, 0.03f);
        taxRate = Math.max(0f, Math.min(0.3f, taxRate));

        return new LocalEconomy(econSeed, priceModifiers, supplyLevels, specializations, blackMarket, taxRate);
    }

    private void applyIndustryPricing(IndustryType industry, Random rng,
                                       Map<String, Float> prices, Map<String, SupplyLevel> supply,
                                       List<String> specializations) {
        switch (industry) {
            case MINING -> {
                specializations.addAll(List.of("iron_ore", "copper", "titanium"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.5f, 0.7f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("food_rations", RngUtil.range(rng, 1.2f, 1.5f)); supply.put("food_rations", SupplyLevel.SCARCE);
                prices.put("medical_supplies", RngUtil.range(rng, 1.3f, 1.6f)); supply.put("medical_supplies", SupplyLevel.SCARCE);
            }
            case AGRICULTURAL -> {
                specializations.addAll(List.of("food_rations", "water"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.4f, 0.6f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("manufactured_parts", RngUtil.range(rng, 1.3f, 1.5f)); supply.put("manufactured_parts", SupplyLevel.SCARCE);
                prices.put("military_electronics", RngUtil.range(rng, 1.5f, 2.0f));
            }
            case INDUSTRIAL -> {
                specializations.addAll(List.of("manufactured_parts", "tungsten_alloy", "synthetic_textiles"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.6f, 0.8f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("iron_ore", RngUtil.range(rng, 1.2f, 1.4f)); supply.put("iron_ore", SupplyLevel.SCARCE);
            }
            case HIGH_TECH -> {
                specializations.addAll(List.of("military_electronics", "lithium_cells", "zero_point_cells"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.6f, 0.8f)); supply.put(s, SupplyLevel.NORMAL); }
                prices.put("food_rations", RngUtil.range(rng, 1.1f, 1.3f));
            }
            case MILITARY -> {
                specializations.addAll(List.of("military_electronics", "tungsten_alloy"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.7f, 0.9f)); supply.put(s, SupplyLevel.NORMAL); }
                prices.put("luxury_goods", RngUtil.range(rng, 1.4f, 1.8f)); supply.put("luxury_goods", SupplyLevel.SCARCE);
            }
            case RESORT -> {
                specializations.addAll(List.of("luxury_goods", "food_rations"));
                prices.put("luxury_goods", RngUtil.range(rng, 0.7f, 0.9f)); supply.put("luxury_goods", SupplyLevel.SURPLUS);
                prices.put("manufactured_parts", RngUtil.range(rng, 1.3f, 1.6f)); supply.put("manufactured_parts", SupplyLevel.SCARCE);
            }
            case OUTPOST -> {
                prices.put("food_rations", RngUtil.range(rng, 1.3f, 1.8f)); supply.put("food_rations", SupplyLevel.SCARCE);
                prices.put("water", RngUtil.range(rng, 1.2f, 1.6f)); supply.put("water", SupplyLevel.SCARCE);
                prices.put("medical_supplies", RngUtil.range(rng, 1.4f, 2.0f)); supply.put("medical_supplies", SupplyLevel.SCARCE);
            }
        }

        for (String c : COMMON_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.1f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : UNCOMMON_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.15f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : RARE_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.95f, 1.2f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : EXOTIC_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.3f)); supply.putIfAbsent(c, SupplyLevel.SCARCE); }
    }

    private float blackMarketChance(IndustryType industry, int population, int techLevel) {
        float base = switch (industry) {
            case OUTPOST -> 0.6f;
            case MILITARY -> 0.3f;
            case RESORT -> 0.2f;
            default -> 0.4f;
        };
        if (population < 5000) base += 0.1f;
        if (techLevel < 2) base += 0.1f;
        return Math.min(0.9f, base);
    }

    private float baseTaxForIndustry(IndustryType industry) {
        return switch (industry) {
            case OUTPOST -> 0.02f;
            case MILITARY -> 0.15f;
            case HIGH_TECH -> 0.12f;
            case RESORT -> 0.1f;
            case INDUSTRIAL -> 0.08f;
            default -> 0.05f;
        };
    }
}
