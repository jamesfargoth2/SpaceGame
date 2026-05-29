package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.stats.MassStatModel;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seed → fully-statted {@link CreatureSpec}. The single entry point for creature generation. */
public final class CreatureGenerator {

    private final FaunaDataRegistry registry;
    private final CreatureAssembler assembler;
    private final MassStatModel statModel = new MassStatModel();

    public CreatureGenerator(FaunaDataRegistry registry) {
        this.registry = registry;
        this.assembler = new CreatureAssembler(registry);
    }

    /** Generate using a specific archetype id. */
    public CreatureSpec generate(String archetypeId, long seed) {
        BodyPlanArchetypeDef arch = registry.getArchetype(archetypeId);
        if (arch == null) throw new IllegalArgumentException("Unknown archetype: " + archetypeId);
        return generate(arch, seed);
    }

    /** Generate with a seeded archetype pick from all loaded archetypes (Cycle D adds biome weighting). */
    public CreatureSpec generate(long seed) {
        List<BodyPlanArchetypeDef> all = new ArrayList<>(registry.allArchetypes());
        all.sort((a, b) -> a.id.compareTo(b.id)); // determinism: never depend on map order
        if (all.isEmpty()) throw new IllegalStateException("No archetypes loaded");
        Random pickRng = new Random(SeedDeriver.forId(SeedDeriver.faunaDomain(seed), 0xA5));
        return generate(all.get(pickRng.nextInt(all.size())), seed);
    }

    private CreatureSpec generate(BodyPlanArchetypeDef arch, long seed) {
        CreatureSpec spec = assembler.assemble(arch, seed);
        float volume = 0f;
        for (AssembledNode n : spec.allNodes) {
            float s = n.scale;
            volume += n.part.geometry.approxVolume() * s * s * s;
        }
        spec.mass = statModel.mass(volume, arch.density);
        float[] stats = statModel.deriveStats(spec.mass, arch);
        spec.maxHP = stats[0];
        spec.moveSpeed = stats[1];
        spec.meleeDamage = stats[2];
        return spec;
    }
}
