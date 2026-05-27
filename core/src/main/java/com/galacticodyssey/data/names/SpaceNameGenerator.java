package com.galacticodyssey.data.names;

import java.util.Random;

/**
 * Category-specific convenience API for generating space-themed names.
 * Wraps {@link NameGenerator} with pre-configured language styles for
 * stars, planets, factions, characters, ships, and stations.
 */
public final class SpaceNameGenerator {

    private static final String[] SHIP_PREFIXES = {
            "ISV", "UNSS", "FCS", "RSV", "TMS", "DSV", "MCS", "ACS"
    };

    private static final String[] SHIP_NOUNS = {
            "Resolve", "Vigilance", "Endurance", "Prometheus", "Harbinger",
            "Wanderer", "Nemesis", "Ascendant", "Relentless", "Pioneer"
    };

    private static final String[] STATION_TYPES = {
            "Station", "Waypoint", "Depot", "Outpost"
    };

    private final NameGenerator generator;

    public SpaceNameGenerator() {
        this.generator = new NameGenerator();
    }

    public SpaceNameGenerator(NameGenerator generator) {
        this.generator = generator;
    }

    /** Generates a star name using the STAR_SCIENTIFIC style. */
    public String starName(Random rng) {
        return generator.generate(LanguageStyles.STAR_SCIENTIFIC, rng, "star");
    }

    /** Generates a planet name using the HUMAN_COLONY style. */
    public String planetName(Random rng) {
        return generator.generate(LanguageStyles.HUMAN_COLONY, rng, "planet");
    }

    /** Generates a faction name using FACTION_IMPERIAL or ALIEN_HARSH style. */
    public String factionName(Random rng) {
        LanguageStyle style = rng.nextBoolean()
                ? LanguageStyles.FACTION_IMPERIAL
                : LanguageStyles.ALIEN_HARSH;
        return generator.generate(style, rng, "faction");
    }

    /** Generates a character name using the HUMAN_COLONY style. */
    public String characterName(Random rng) {
        return generator.generate(LanguageStyles.HUMAN_COLONY, rng, "character");
    }

    /** Generates a ship name in "PREFIX Noun" format. */
    public String shipName(Random rng) {
        String prefix = SHIP_PREFIXES[rng.nextInt(SHIP_PREFIXES.length)];
        String noun = SHIP_NOUNS[rng.nextInt(SHIP_NOUNS.length)];
        return prefix + " " + noun;
    }

    /** Generates a station name in "factionPrefix Type NNN" format. */
    public String stationName(String factionPrefix, Random rng) {
        String type = STATION_TYPES[rng.nextInt(STATION_TYPES.length)];
        int number = rng.nextInt(900) + 100; // 100-999
        return factionPrefix + " " + type + " " + number;
    }

    /** Resets the underlying name generator's uniqueness tracking. */
    public void reset() {
        generator.reset();
    }
}
