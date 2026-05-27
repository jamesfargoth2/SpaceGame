package com.galacticodyssey.data.names;

/**
 * Built-in language style constants for procedural name generation.
 * Each style targets a different naming flavour (scientific catalogues,
 * human colonies, alien species, imperial factions, etc.).
 */
public final class LanguageStyles {

    private LanguageStyles() {}

    public static final LanguageStyle STAR_SCIENTIFIC = LanguageStyle.builder("star_scientific")
            .setOnsets("K", "T", "Pr", "Gl", "V", "X", "S", "Cr", "Z", "Al")
            .setNuclei("a", "e", "i", "o", "u", "ei", "au", "ae")
            .setCodaChance(0.4f)
            .setSyllableCount(2, 2, 3)
            .setSuffixes("-A", "-B", "-Prime", " Major", " Minor")
            .setSuffixChance(0.25f)
            .build();

    public static final LanguageStyle HUMAN_COLONY = LanguageStyle.builder("human_colony")
            .setOnsets("", "b", "c", "d", "f", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v")
            .setNuclei("a", "e", "i", "o", "u")
            .setCodaChance(0.5f)
            .setSyllableCount(2, 2, 2, 3)
            .build();

    public static final LanguageStyle ALIEN_HARSH = LanguageStyle.builder("alien_harsh")
            .setOnsets("Kr", "Zx", "Gh", "Vr", "Xk", "Tz", "Kh", "Gr", "Br")
            .setNuclei("a", "aa", "u", "uu", "o", "i")
            .setCodaChance(0.7f)
            .setSyllableCount(1, 2, 2, 3)
            .setFrontVowels("i")
            .setBackVowels("a", "aa", "u", "uu", "o")
            .build();

    public static final LanguageStyle ALIEN_SOFT = LanguageStyle.builder("alien_soft")
            .setOnsets("L", "N", "S", "Sh", "M", "Y", "W", "", "")
            .setNuclei("ia", "ae", "io", "ua", "ui", "ai", "ei")
            .setCodaChance(0.2f)
            .setSyllableCount(3, 3, 4, 4)
            .setFrontVowels("ia", "ae", "ei", "ai")
            .setBackVowels("io", "ua", "ui")
            .build();

    public static final LanguageStyle FACTION_IMPERIAL = LanguageStyle.builder("faction_imperial")
            .setOnsets("Im", "Dom", "Ter", "Aur", "Sol", "Cael", "Val", "Reg")
            .setNuclei("a", "i", "e", "u", "ae", "ia")
            .setCodas("us", "um", "is", "ia")
            .setCodaChance(0.8f)
            .setSuffixes(" Empire", " Dominion", " Hegemony", " Imperium")
            .setSuffixChance(0.5f)
            .setFrontVowels("i", "e", "ae", "ia")
            .setBackVowels("a", "u")
            .build();
}
