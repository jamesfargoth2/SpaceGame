package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.BackstoryHook;
import com.galacticodyssey.npc.HookType;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.PersonalityTrait;
import com.galacticodyssey.npc.components.NpcBackstoryComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class NpcGenerator {

    private static final String[] HOOK_SUMMARIES = {
        "Owes a substantial debt to a powerful creditor.",
        "Searching for a family member lost during a colony evacuation.",
        "Wanted by authorities for past crimes.",
        "Served in a military campaign and carries the scars.",
        "Possesses knowledge someone powerful wants kept secret.",
        "Has an unresolved rivalry with another spacer."
    };

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
        float[] baseStats = new float[] {
            rollBase(npcSeed, 10), // accuracy
            rollBase(npcSeed, 11), // repair
            rollBase(npcSeed, 12), // medical
            rollBase(npcSeed, 13), // piloting
            rollBase(npcSeed, 14), // science
            rollBase(npcSeed, 15), // combat
            rollBase(npcSeed, 16), // persuasion
            rollBase(npcSeed, 17), // stealth
        };
        float[] weights = roleStatWeights(role);
        float[] speciesMods = {
            species.accuracyMod, species.repairMod, species.medicalMod,
            species.pilotingMod, species.scienceMod, species.combatMod,
            species.persuasionMod, species.stealthMod
        };
        float[] bgMods = {
            background.accuracyMod, background.repairMod, background.medicalMod,
            background.pilotingMod, background.scienceMod, background.combatMod,
            background.persuasionMod, background.stealthMod
        };
        for (int i = 0; i < 8; i++) {
            baseStats[i] = clampStat(baseStats[i] * weights[i] + speciesMods[i] + bgMods[i]);
        }
        stats.accuracy   = baseStats[0];
        stats.repair     = baseStats[1];
        stats.medical    = baseStats[2];
        stats.piloting   = baseStats[3];
        stats.science    = baseStats[4];
        stats.combat     = baseStats[5];
        stats.persuasion = baseStats[6];
        stats.stealth    = baseStats[7];
        entity.add(stats);

        NpcPersonalityComponent personality = generatePersonality(npcSeed);
        entity.add(personality);

        NpcBackstoryComponent backstory = generateBackstory(npcSeed, role);
        entity.add(backstory);

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

    private NpcBackstoryComponent generateBackstory(long npcSeed, NPCRole role) {
        Random rng = new Random(SeedDeriver.forId(npcSeed, 40));
        NpcBackstoryComponent backstory = new NpcBackstoryComponent();
        HookType[] allHooks = HookType.values();

        int hookCount = 1 + rng.nextInt(2); // 1-2
        Set<HookType> usedTypes = new HashSet<>();

        if (role == NPCRole.PIRATE_CAPTAIN) {
            backstory.hooks.add(makeHook(HookType.WANTED_CRIMINAL, rng));
            usedTypes.add(HookType.WANTED_CRIMINAL);
        }

        while (backstory.hooks.size() < hookCount) {
            HookType type = allHooks[rng.nextInt(allHooks.length)];
            if (usedTypes.contains(type)) continue;
            backstory.hooks.add(makeHook(type, rng));
            usedTypes.add(type);
        }

        return backstory;
    }

    private BackstoryHook makeHook(HookType type, Random rng) {
        boolean revealed = rng.nextFloat() < 0.3f;
        long questSeed = rng.nextLong();
        String summary = HOOK_SUMMARIES[type.ordinal()];
        return new BackstoryHook(type, revealed, questSeed, summary);
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

    private float[] roleStatWeights(NPCRole role) {
        float[] w = {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
        if (role == null) return w;
        switch (role) {
            case PILOT:              w[3] = 1.4f; w[0] = 1.15f; break; // piloting primary, accuracy secondary
            case ENGINEER:           w[1] = 1.4f; w[4] = 1.15f; break; // repair primary, science secondary
            case GUNNER:             w[0] = 1.4f; w[5] = 1.15f; break; // accuracy primary, combat secondary
            case MEDIC:              w[2] = 1.4f; w[4] = 1.15f; break; // medical primary, science secondary
            case NAVIGATOR:          w[3] = 1.4f; w[4] = 1.15f; break; // piloting primary, science secondary
            case SCIENCE_OFFICER:    w[4] = 1.4f; w[2] = 1.15f; break; // science primary, medical secondary
            case MARINE:             w[5] = 1.4f; w[0] = 1.15f; break; // combat primary, accuracy secondary
            case MERCHANT:           w[6] = 1.4f; w[7] = 1.15f; break; // persuasion primary, stealth secondary
            case BARTENDER:          w[6] = 1.4f; w[2] = 1.15f; break; // persuasion primary, medical secondary
            case INFORMATION_BROKER: w[6] = 1.4f; w[7] = 1.15f; break; // persuasion primary, stealth secondary
            case MECHANIC:           w[1] = 1.4f; w[0] = 1.15f; break; // repair primary, accuracy secondary
            case PIRATE_CAPTAIN:     w[5] = 1.4f; w[6] = 1.15f; break; // combat primary, persuasion secondary
            case BOUNTY_HUNTER:      w[5] = 1.4f; w[7] = 1.15f; break; // combat primary, stealth secondary
            case MERCENARY:          w[5] = 1.4f; w[0] = 1.15f; break; // combat primary, accuracy secondary
            case SMUGGLER:           w[7] = 1.4f; w[6] = 1.15f; break; // stealth primary, persuasion secondary
            case COLONIST:           w[1] = 1.4f; w[2] = 1.15f; break; // repair primary, medical secondary
            case SCIENTIST:          w[4] = 1.4f; w[2] = 1.15f; break; // science primary, medical secondary
            default: break;
        }
        return w;
    }

    private float clampStat(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
