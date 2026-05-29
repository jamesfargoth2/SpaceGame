package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.SizeTierDef;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.CityType;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spatial parameters derived from population. Population is the single size driver. */
public final class CitySizeProfile {
    public final CityType type;
    public final float radiusMetres;
    public final boolean hasWall;
    public final float density;          // 0..1
    public final List<CityForm> formBias;

    private CitySizeProfile(CityType type, float radiusMetres, boolean hasWall,
                            float density, List<CityForm> formBias) {
        this.type = type;
        this.radiusMetres = radiusMetres;
        this.hasWall = hasWall;
        this.density = density;
        this.formBias = formBias;
    }

    public static CitySizeProfile from(CityDataRegistry reg, int population, long citySeed) {
        SizeTierDef tier = reg.tierForPopulation(population);
        Random rng = new Random(citySeed ^ 0x512E1A5EL);
        float radius = RngUtil.range(rng, tier.radiusMin, tier.radiusMax);
        boolean wall = resolveWall(tier.wall, rng);
        List<CityForm> bias = new ArrayList<>();
        for (String f : tier.formBias) bias.add(CityForm.valueOf(f));
        return new CitySizeProfile(CityType.valueOf(tier.type), radius, wall, tier.density, bias);
    }

    private static boolean resolveWall(String wall, Random rng) {
        switch (wall) {
            case "yes":   return true;
            case "no":    return false;
            case "maybe": return rng.nextFloat() < 0.5f;
            default:      return false;
        }
    }
}
