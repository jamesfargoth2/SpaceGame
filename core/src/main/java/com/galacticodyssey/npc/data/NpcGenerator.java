package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.PersonalityTrait;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class NpcGenerator {

    private static final String[][] CONTRADICTORY_PAIRS = {
        {"BRAVE", "COWARDLY"},
        {"LOYAL", "VINDICTIVE"},
        {"GENEROUS", "GREEDY"},
        {"DISCIPLINED", "RECKLESS"},
        {"CURIOUS", "PRAGMATIC"},
    };

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
        return generate(engine, seed, speciesId, backgroundId, null);
    }

    public Entity generate(Engine engine, long seed, String speciesId, String backgroundId, NPCRole role) {
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
        identity.recruitable = role != null && role.isCrewRole();
        identity.role = role;
        identity.age = rollAge(npcSeed);
        entity.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy   = clampStat(rollBase(npcSeed, 10) + species.accuracyMod   + background.accuracyMod);
        stats.repair     = clampStat(rollBase(npcSeed, 11) + species.repairMod     + background.repairMod);
        stats.medical    = clampStat(rollBase(npcSeed, 12) + species.medicalMod    + background.medicalMod);
        stats.piloting   = clampStat(rollBase(npcSeed, 13) + species.pilotingMod   + background.pilotingMod);
        stats.science    = clampStat(rollBase(npcSeed, 14) + species.scienceMod    + background.scienceMod);
        stats.combat     = clampStat(rollBase(npcSeed, 15) + species.combatMod     + background.combatMod);
        stats.persuasion = clampStat(rollBase(npcSeed, 16) + species.persuasionMod + background.persuasionMod);
        stats.stealth    = clampStat(rollBase(npcSeed, 17) + species.stealthMod    + background.stealthMod);
        entity.add(stats);

        NpcPersonalityComponent personality = generatePersonality(npcSeed);
        entity.add(personality);

        engine.addEntity(entity);
        return entity;
    }

    private NpcPersonalityComponent generatePersonality(long npcSeed) {
        Random rng = new Random(SeedDeriver.forId(npcSeed, 30));
        NpcPersonalityComponent personality = new NpcPersonalityComponent();

        int traitCount = 2 + rng.nextInt(3); // 2-4
        PersonalityTrait[] allTraits = PersonalityTrait.values();
        Set<String> selectedNames = new HashSet<>();
        int attempts = 0;
        while (personality.traits.size() < traitCount && attempts < 100) {
            attempts++;
            PersonalityTrait candidate = allTraits[rng.nextInt(allTraits.length)];
            if (selectedNames.contains(candidate.name())) continue;
            if (contradictsAny(candidate, selectedNames)) continue;
            personality.traits.add(candidate);
            selectedNames.add(candidate.name());
        }

        personality.loyalty = rng.nextFloat() * 0.8f + 0.1f;  // 0.1-0.9
        personality.greed   = rng.nextFloat() * 0.7f + 0.1f;  // 0.1-0.8
        personality.bravery = rng.nextFloat() * 0.7f + 0.2f;  // 0.2-0.9

        return personality;
    }

    private boolean contradictsAny(PersonalityTrait candidate, Set<String> existing) {
        String name = candidate.name();
        for (String[] pair : CONTRADICTORY_PAIRS) {
            if (pair[0].equals(name) && existing.contains(pair[1])) return true;
            if (pair[1].equals(name) && existing.contains(pair[0])) return true;
        }
        return false;
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

    private float rollAge(long npcSeed) {
        long derived = SeedDeriver.forId(npcSeed, 20);
        float normalized = ((derived & Long.MAX_VALUE) % 10000) / 10000f;
        return normalized * 52f + 18f; // 18-70
    }

    private float clampStat(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
