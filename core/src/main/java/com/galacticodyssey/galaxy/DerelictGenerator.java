package com.galacticodyssey.galaxy;

import java.util.*;

public final class DerelictGenerator {

    private static final String[] ALL_MODULES = {
        "bridge", "engine_room", "cargo_bay", "crew_quarters",
        "medbay", "armory", "engineering", "life_support"
    };

    private static final DerelictCause[] CAUSES = DerelictCause.values();

    public DerelictWreck generate(long seed, int hullClass) {
        long derelictSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.DERELICT_DOMAIN), 0);
        Random rng = new Random(derelictSeed);

        DerelictCause cause = CAUSES[rng.nextInt(CAUSES.length)];
        float damageLevel = RngUtil.range(rng, 0.3f, 1.0f);

        List<String> remainingModules = new ArrayList<>();
        int moduleCount = Math.max(1, (int) (ALL_MODULES.length * (1f - damageLevel * 0.8f)));
        List<String> pool = new ArrayList<>(Arrays.asList(ALL_MODULES));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < moduleCount && i < pool.size(); i++) {
            remainingModules.add(pool.get(i));
        }

        EnumSet<WreckHazard> hazards = rollHazards(cause, rng);
        int lootTier = Math.max(1, hullClass - (int) (damageLevel * 3));
        List<String> logEntries = generateLogs(cause, rng);

        return new DerelictWreck(derelictSeed, hullClass, damageLevel,
            remainingModules, hazards, lootTier, logEntries, cause);
    }

    private EnumSet<WreckHazard> rollHazards(DerelictCause cause, Random rng) {
        EnumSet<WreckHazard> hazards = EnumSet.noneOf(WreckHazard.class);
        for (WreckHazard hazard : WreckHazard.values()) {
            float chance = getHazardChance(cause, hazard);
            if (rng.nextFloat() < chance) {
                hazards.add(hazard);
            }
        }
        if (hazards.isEmpty()) {
            hazards.add(WreckHazard.VACUUM_BREACH);
        }
        return hazards;
    }

    private float getHazardChance(DerelictCause cause, WreckHazard hazard) {
        return switch (cause) {
            case PIRATE_ATTACK -> switch (hazard) {
                case VACUUM_BREACH -> 0.8f;
                case STRUCTURAL_COLLAPSE -> 0.4f;
                case AUTOMATED_DEFENSES -> 0.2f;
                default -> 0.05f;
            };
            case REACTOR_FAILURE -> switch (hazard) {
                case RADIATION -> 0.9f;
                case TOXIC_ATMOSPHERE -> 0.5f;
                case STRUCTURAL_COLLAPSE -> 0.3f;
                default -> 0.05f;
            };
            case ALIEN_ENCOUNTER -> switch (hazard) {
                case HOSTILE_FAUNA -> 0.6f;
                case RADIATION -> 0.3f;
                case TOXIC_ATMOSPHERE -> 0.4f;
                default -> 0.1f;
            };
            case MUTINY -> switch (hazard) {
                case AUTOMATED_DEFENSES -> 0.5f;
                case VACUUM_BREACH -> 0.3f;
                case STRUCTURAL_COLLAPSE -> 0.2f;
                default -> 0.05f;
            };
            case COLLISION -> switch (hazard) {
                case VACUUM_BREACH -> 0.9f;
                case STRUCTURAL_COLLAPSE -> 0.7f;
                case RADIATION -> 0.2f;
                default -> 0.05f;
            };
            case PLAGUE -> switch (hazard) {
                case TOXIC_ATMOSPHERE -> 0.8f;
                case HOSTILE_FAUNA -> 0.3f;
                default -> 0.05f;
            };
            case UNKNOWN -> switch (hazard) {
                case RADIATION -> 0.2f;
                case AUTOMATED_DEFENSES -> 0.3f;
                default -> 0.1f;
            };
        };
    }

    private List<String> generateLogs(DerelictCause cause, Random rng) {
        int logCount = RngUtil.range(rng, 2, 5);
        List<String> logs = new ArrayList<>();
        for (int i = 0; i < logCount; i++) {
            logs.add(generateLogEntry(cause, rng));
        }
        return logs;
    }

    private String generateLogEntry(DerelictCause cause, Random rng) {
        String[] templates = getTemplatesForCause(cause);
        String template = templates[rng.nextInt(templates.length)];
        template = template.replace("{deck}", String.valueOf(RngUtil.range(rng, 1, 8)));
        template = template.replace("{deck2}", String.valueOf(RngUtil.range(rng, 3, 12)));
        template = template.replace("{number}", String.valueOf(RngUtil.range(rng, 1, 5)));
        template = template.replace("{percent}", String.valueOf(RngUtil.range(rng, 10, 90)));
        template = template.replace("{years}", String.valueOf(RngUtil.range(rng, 5, 500)));
        template = template.replace("{time}", String.format("%02d:%02d", RngUtil.range(rng, 0, 24), RngUtil.range(rng, 0, 60)));
        template = template.replace("{location}", pickLocation(rng));
        template = template.replace("{direction}", pickDirection(rng));
        return template;
    }

    private String[] getTemplatesForCause(DerelictCause cause) {
        return switch (cause) {
            case PIRATE_ATTACK -> new String[]{
                "Mayday! Multiple hostiles on approach vector—",
                "Hull breach on deck {deck}. Weapons offline.",
                "Captain's log: We tried to outrun them near {location}. We couldn't.",
                "Last entry. If anyone finds this... the pirates came from the {direction} belt."
            };
            case REACTOR_FAILURE -> new String[]{
                "Engineering report: containment field fluctuation in reactor {number}.",
                "Emergency shutdown failed. Radiation levels critical on decks {deck} through {deck2}.",
                "All hands abandon ship. Repeat: abandon ship. Reactor cascade imminent.",
                "Final log: coolant system failed at {time}. Crew evacuation at {percent}%."
            };
            case ALIEN_ENCOUNTER -> new String[]{
                "Contact! Unknown vessel. No transponder. No known configuration.",
                "They're inside the ship. Deck {deck} is... changing.",
                "It doesn't respond to any frequency. Crew reporting hallucinations.",
                "I don't think they wanted to hurt us. I think they just didn't notice us."
            };
            case MUTINY -> new String[]{
                "Security alert: unauthorized access to armory on deck {deck}.",
                "First Mate's log: The captain has gone too far. We have no choice.",
                "Gunfire in the corridors. Both sides have sealed their sections.",
                "It's over. Half the crew is gone. The other half won't last without supplies."
            };
            case COLLISION -> new String[]{
                "Navigation error. Object on collision course. Brace for—",
                "Sensors didn't pick it up. Too small for the array, too big to survive.",
                "Structural integrity at {percent}%. Main spine fractured in three places.",
                "Emergency beacon activated. We are adrift. Life support on backup."
            };
            case PLAGUE -> new String[]{
                "Medical log: Unidentified pathogen. Quarantine on deck {deck}.",
                "Day {number}: Infection rate now {percent}%. No treatment effective.",
                "The medbay is overrun. We're sealing the ship and transmitting a warning.",
                "If you're reading this, do NOT board. The pathogen is airborne."
            };
            case UNKNOWN -> new String[]{
                "Systems nominal. All readings green. Crew complement: 0.",
                "There's no damage. No struggle. They're just... gone.",
                "Automated systems running. Ship in perfect condition. Date stamp: {years} years ago.",
                "Every personal item is in place. Every meal half-eaten. No bodies."
            };
        };
    }

    private String pickLocation(Random rng) {
        String[] locations = {"the outer ring", "sector seven", "the trade lanes", "deep space", "the nebula edge"};
        return locations[rng.nextInt(locations.length)];
    }

    private String pickDirection(Random rng) {
        String[] directions = {"northern", "southern", "eastern", "western", "inner", "outer"};
        return directions[rng.nextInt(directions.length)];
    }
}
