package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.DistrictMixDef;
import com.galacticodyssey.city.data.FunctionWeight;
import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.List;
import java.util.Random;

/** Tags each lot with a BuildingFunction from its district mix; landmark lots get special functions. */
public final class LotFunctionAssigner {
    private LotFunctionAssigner() {}

    public static void assign(List<BuildingLot> lots, List<Landmark> landmarks,
                              CityDataRegistry reg, long citySeed) {
        long domain = SeedDeriver.forId(citySeed, 0xF0C70F00L);
        long idx = 0;
        for (BuildingLot lot : lots) {
            Random rng = new Random(SeedDeriver.forId(domain, idx++));

            BuildingFunction special = landmarkFunction(lot, landmarks);
            if (special != null) {
                lot.function = special;
                continue;
            }
            DistrictMixDef mix = reg.districtMix(lot.district);
            lot.function = weightedPick(mix, rng);
        }
    }

    private static BuildingFunction landmarkFunction(BuildingLot lot, List<Landmark> landmarks) {
        for (Landmark l : landmarks) {
            if (lot.footprint.contains(l.position.x, l.position.y)) {
                switch (l.type) {
                    case FACTION_LANDMARK: return BuildingFunction.FACTION_HQ;
                    case CIVIC_CENTRE:     return BuildingFunction.TOWN_HALL;
                    case SPACEPORT:        return BuildingFunction.TERMINAL;
                    case MARKET_PLAZA:     return BuildingFunction.MARKET_STALL;
                    default:               return null;
                }
            }
        }
        return null;
    }

    private static BuildingFunction weightedPick(DistrictMixDef mix, Random rng) {
        int total = 0;
        for (FunctionWeight fw : mix.functions) total += Math.max(0, fw.weight);
        if (total <= 0) return BuildingFunction.EMPTY_LOT;
        int roll = rng.nextInt(total);
        for (FunctionWeight fw : mix.functions) {
            roll -= Math.max(0, fw.weight);
            if (roll < 0) return fw.function;
        }
        return mix.functions.get(mix.functions.size() - 1).function;
    }
}
