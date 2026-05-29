package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks a CityForm from the combined tier + faction bias pools (each entry = one vote). */
public final class CityFormSelector {
    private CityFormSelector() {}

    public static CityForm select(CityDataRegistry reg, FactionEthos ethos,
                                  CitySizeProfile profile, long citySeed) {
        List<CityForm> pool = new ArrayList<>(profile.formBias);
        pool.addAll(reg.factionFormBias(ethos));
        if (pool.isEmpty()) return CityForm.GRID; // safe default
        Random rng = new Random(citySeed ^ 0xF0125B1A5L);
        return pool.get(rng.nextInt(pool.size()));
    }
}
