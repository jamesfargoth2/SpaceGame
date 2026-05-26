package com.galacticodyssey.npc;

import com.galacticodyssey.data.names.LanguageStyle;
import com.galacticodyssey.data.names.LanguageStyles;
import com.galacticodyssey.data.names.NameGenerator;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Procedural NPC generator. Produces deterministic {@link NPCData} instances
 * from a seed, with role-based stat distribution, personality-trait selection
 * (with contradiction rejection), and backstory hooks.
 */
public final class NPCGenerator {

    private static final String[][] CONTRADICTORY_PAIRS = {
            {"BRAVE", "COWARDLY"},
            {"LOYAL", "VINDICTIVE"},
            {"GENEROUS", "GREEDY"},
            {"DISCIPLINED", "RECKLESS"},
            {"CURIOUS", "PRAGMATIC"},
    };

    private static final HookType[] ALL_HOOKS = HookType.values();

    private static final String[] HOOK_SUMMARIES = {
            "Owes a substantial debt to a powerful creditor.",
            "Searching for a family member lost during a colony evacuation.",
            "Wanted by authorities for past crimes.",
            "Served in a military campaign and carries the scars.",
            "Possesses knowledge someone powerful wants kept secret.",
            "Has an unresolved rivalry with another spacer."
    };

    private final NameGenerator nameGenerator;

    public NPCGenerator() {
        this.nameGenerator = new NameGenerator();
    }

    public NPCGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    /**
     * Generates a deterministic NPC from the given role, optional faction, and seed.
     */
    public NPCData generate(NPCRole role, String factionId, long seed) {
        Random rng = new Random(seed);

        // Species selection: 70% human, 15% alien_harsh, 15% alien_soft
        String species = pickSpecies(rng);

        // Name generation based on species
        LanguageStyle nameStyle = nameStyleForSpecies(species);
        String name = nameGenerator.generate(nameStyle, rng, "npc");

        // ID derived from seed
        String id = "npc-" + Long.toHexString(seed);

        // Age: 18-70
        float age = RngUtil.range(rng, 18f, 70f);

        // Stats
        float[] stats = generateStats(role, rng);

        // Personality traits (2-4, unique, no contradictions)
        List<PersonalityTrait> traits = generateTraits(rng);

        // Personality scores
        float loyalty = RngUtil.range(rng, 0.1f, 0.9f);
        float greed = RngUtil.range(rng, 0.1f, 0.9f);
        float bravery = RngUtil.range(rng, 0.1f, 0.9f);

        // Backstory hooks (1-2)
        List<BackstoryHook> hooks = generateHooks(role, rng);

        long appearanceSeed = rng.nextLong();

        return new NPCData(id, name, role, factionId,
                age, species,
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5], stats[6],
                traits, loyalty, greed, bravery,
                hooks, appearanceSeed);
    }

    private String pickSpecies(Random rng) {
        float roll = rng.nextFloat();
        if (roll < 0.70f) return "human";
        if (roll < 0.85f) return "alien_harsh";
        return "alien_soft";
    }

    private LanguageStyle nameStyleForSpecies(String species) {
        switch (species) {
            case "alien_harsh":
                return LanguageStyles.ALIEN_HARSH;
            case "alien_soft":
                return LanguageStyles.ALIEN_SOFT;
            default:
                return LanguageStyles.HUMAN_COLONY;
        }
    }

    /**
     * Generates stats as [piloting, engineering, combat, medicine, science, persuasion, stealth].
     * Role determines which stat is primary (0.55-0.95) and secondary (0.35-0.65);
     * the rest are base (0.1-0.4). All stats receive +/-0.1 noise and are clamped to [0, 1].
     */
    private float[] generateStats(NPCRole role, Random rng) {
        // Indices: 0=piloting, 1=engineering, 2=combat, 3=medicine, 4=science, 5=persuasion, 6=stealth
        int primaryIdx = -1;
        int secondaryIdx = -1;

        switch (role) {
            case PILOT:
                primaryIdx = 0;
                secondaryIdx = 2;
                break;
            case ENGINEER:
                primaryIdx = 1;
                secondaryIdx = 4;
                break;
            case GUNNER:
                primaryIdx = 2;
                secondaryIdx = 0;
                break;
            case MEDIC:
                primaryIdx = 3;
                secondaryIdx = 4;
                break;
            case MERCHANT:
                primaryIdx = 5;
                secondaryIdx = 6;
                break;
            case PIRATE_CAPTAIN:
                primaryIdx = 2;
                secondaryIdx = 5;
                break;
            default:
                break;
        }

        float[] stats = new float[7];
        for (int i = 0; i < 7; i++) {
            float base;
            if (i == primaryIdx) {
                base = RngUtil.range(rng, 0.55f, 0.95f);
            } else if (i == secondaryIdx) {
                base = RngUtil.range(rng, 0.35f, 0.65f);
            } else {
                base = RngUtil.range(rng, 0.1f, 0.4f);
            }
            // Apply noise
            float noise = RngUtil.range(rng, -0.1f, 0.1f);
            stats[i] = clamp(base + noise, 0f, 1f);
        }
        return stats;
    }

    private List<PersonalityTrait> generateTraits(Random rng) {
        int traitCount = RngUtil.range(rng, 2, 5); // 2-4 inclusive
        PersonalityTrait[] allTraits = PersonalityTrait.values();
        List<PersonalityTrait> selected = new ArrayList<>(traitCount);
        Set<String> selectedNames = new HashSet<>();

        int attempts = 0;
        while (selected.size() < traitCount && attempts < 100) {
            attempts++;
            PersonalityTrait candidate = allTraits[rng.nextInt(allTraits.length)];
            if (selectedNames.contains(candidate.name())) {
                continue;
            }
            if (contradictsAny(candidate, selectedNames)) {
                continue;
            }
            selected.add(candidate);
            selectedNames.add(candidate.name());
        }
        return selected;
    }

    private boolean contradictsAny(PersonalityTrait candidate, Set<String> existing) {
        String name = candidate.name();
        for (String[] pair : CONTRADICTORY_PAIRS) {
            if (pair[0].equals(name) && existing.contains(pair[1])) return true;
            if (pair[1].equals(name) && existing.contains(pair[0])) return true;
        }
        return false;
    }

    private List<BackstoryHook> generateHooks(NPCRole role, Random rng) {
        int hookCount = RngUtil.range(rng, 1, 3); // 1-2 inclusive
        List<BackstoryHook> hooks = new ArrayList<>(hookCount);
        Set<HookType> usedTypes = new HashSet<>();

        // Pirates always get WANTED_CRIMINAL
        if (role == NPCRole.PIRATE_CAPTAIN) {
            hooks.add(makeHook(HookType.WANTED_CRIMINAL, rng));
            usedTypes.add(HookType.WANTED_CRIMINAL);
        }

        while (hooks.size() < hookCount) {
            HookType type = ALL_HOOKS[rng.nextInt(ALL_HOOKS.length)];
            if (usedTypes.contains(type)) continue;
            hooks.add(makeHook(type, rng));
            usedTypes.add(type);
        }

        return hooks;
    }

    private BackstoryHook makeHook(HookType type, Random rng) {
        boolean revealed = rng.nextFloat() < 0.3f;
        long questSeed = rng.nextLong();
        String summary = HOOK_SUMMARIES[type.ordinal()];
        return new BackstoryHook(type, revealed, questSeed, summary);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
