package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;

import java.util.List;

public class NpcGenerator {

    private final NpcDataRegistry registry;

    public NpcGenerator(NpcDataRegistry registry) {
        this.registry = registry;
    }

    public Entity generate(Engine engine, long seed) {
        long npcSeed = SeedDeriver.npcDomain(seed);

        List<String> speciesIds = registry.getSpeciesIds();
        String speciesId = speciesIds.get(pickIndex(npcSeed, 0, speciesIds.size()));

        List<BackgroundDefinition> backgrounds = registry.getAllBackgrounds();
        String backgroundId = backgrounds.get(pickIndex(npcSeed, 1, backgrounds.size())).id;

        return generate(engine, seed, speciesId, backgroundId);
    }

    public Entity generate(Engine engine, long seed, String speciesId, String backgroundId) {
        long npcSeed = SeedDeriver.npcDomain(seed);
        SpeciesDefinition species = registry.getSpecies(speciesId);
        BackgroundDefinition background = registry.getBackground(backgroundId);

        Entity entity = new Entity();

        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.npcId = "npc_" + Long.toHexString(npcSeed);
        identity.species = speciesId;
        identity.background = backgroundId;
        identity.name = pickName(npcSeed, speciesId);
        identity.portraitId = pickFromList(npcSeed, 6, species.portraitIds);
        identity.disposition = NpcDisposition.NEUTRAL;
        identity.recruitable = false;
        entity.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = clampStat(rollBase(npcSeed, 10) + species.accuracyMod + background.accuracyMod);
        stats.repair   = clampStat(rollBase(npcSeed, 11) + species.repairMod   + background.repairMod);
        stats.medical  = clampStat(rollBase(npcSeed, 12) + species.medicalMod  + background.medicalMod);
        stats.piloting = clampStat(rollBase(npcSeed, 13) + species.pilotingMod + background.pilotingMod);
        stats.science  = clampStat(rollBase(npcSeed, 14) + species.scienceMod  + background.scienceMod);
        stats.combat   = clampStat(rollBase(npcSeed, 15) + species.combatMod   + background.combatMod);
        entity.add(stats);

        engine.addEntity(entity);
        return entity;
    }

    private String pickName(long npcSeed, String speciesId) {
        NpcDataRegistry.NamePool pool = registry.getNamePool(speciesId);
        if (pool == null || pool.firstNames.isEmpty()) return "Unknown";
        String first = pickFromList(npcSeed, 4, pool.firstNames);
        String last  = pickFromList(npcSeed, 5, pool.lastNames);
        return first + " " + last;
    }

    private <T> T pickFromList(long seed, int slot, List<T> list) {
        if (list.isEmpty()) return null;
        return list.get(pickIndex(seed, slot, list.size()));
    }

    private int pickIndex(long seed, int slot, int listSize) {
        long derived = SeedDeriver.forId(seed, slot);
        return (int) ((derived & Long.MAX_VALUE) % listSize);
    }

    private float rollBase(long seed, int slot) {
        long derived = SeedDeriver.forId(seed, slot);
        float normalized = ((derived & Long.MAX_VALUE) % 10000) / 10000f;
        return normalized * 60f + 20f;
    }

    private float clampStat(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
