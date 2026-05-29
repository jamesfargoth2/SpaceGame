package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.Random;

/** Orchestrates the staged pipeline into a complete, deterministic CityLayout. */
public final class CityLayoutGenerator {
    private final CityDataRegistry registry;
    private final SpaceNameGenerator nameGen;

    public CityLayoutGenerator(CityDataRegistry registry) {
        this(registry, new SpaceNameGenerator());
    }

    public CityLayoutGenerator(CityDataRegistry registry, SpaceNameGenerator nameGen) {
        this.registry = registry;
        this.nameGen = nameGen;
    }

    public CityLayout generate(CityRequest req) {
        long citySeed = SeedDeriver.cityDomain(req.seed);

        CitySizeProfile profile = CitySizeProfile.from(registry, req.population, citySeed);
        CityLayout layout = new CityLayout();
        layout.cityId = citySeed;
        layout.seed = req.seed;
        layout.population = req.population;
        layout.rulingEthos = req.rulingEthos;
        layout.factionId = req.factionId;
        layout.type = profile.type;
        layout.form = CityFormSelector.select(registry, req.rulingEthos, profile, citySeed);

        SpaceNameGenerator freshNameGen = new SpaceNameGenerator();
        layout.name = freshNameGen.cityName(new Random(SeedDeriver.forId(citySeed, 0x4A33L)));

        layout.landmarks.addAll(LandmarkPlacer.place(
                profile.radiusMetres, req.hasSpaceport, req.authoredLandmarks, citySeed));

        StreetNetwork net = StreetNetworkBuilder.build(
                layout.form, profile.radiusMetres, profile.density, citySeed);
        layout.streets.addAll(net.streets);
        layout.blocks.addAll(net.blocks);

        DistrictZoner.zone(layout.blocks, layout.landmarks, profile.radiusMetres, citySeed);
        layout.lots.addAll(LotSubdivider.subdivide(layout.blocks, registry, citySeed));
        LotFunctionAssigner.assign(layout.lots, layout.landmarks, registry, citySeed);

        if (profile.hasWall && !layout.blocks.isEmpty()) {
            layout.wall = WallBuilder.build(layout.blocks);
        }

        TerrainConformer.conform(layout.streets, layout.lots, req.terrain);
        return layout;
    }
}
