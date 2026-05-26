package com.galacticodyssey.data.names;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Core procedural name generation engine. Builds names from phoneme-chain
 * syllable assembly with vowel harmony, pronounceability filtering, and
 * per-category uniqueness tracking.
 */
public final class NameGenerator {

    private static final int MAX_RETRIES = 20;
    private static final int MAX_CONSECUTIVE_CONSONANTS = 4;
    private static final int MIN_NAME_LENGTH = 2;

    private final Map<String, Set<String>> usedNames = new HashMap<>();

    /**
     * Generates a unique name for the given category using the specified language style.
     * If all simple names are exhausted after MAX_RETRIES attempts, a numeric suffix is appended.
     */
    public String generate(LanguageStyle style, Random rng, String category) {
        Set<String> used = usedNames.computeIfAbsent(category, k -> new HashSet<>());

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String name = buildName(style, rng);
            if (!used.contains(name)) {
                used.add(name);
                return name;
            }
        }

        // Fallback: append numeric suffix to guarantee uniqueness
        String baseName = buildName(style, rng);
        int counter = 1;
        String candidate = baseName + "-" + String.format("%03d", counter);
        while (used.contains(candidate)) {
            counter++;
            candidate = baseName + "-" + String.format("%03d", counter);
        }
        used.add(candidate);
        return candidate;
    }

    /**
     * Builds a raw name from syllable assembly with vowel harmony and pronounceability filtering.
     */
    public String buildName(LanguageStyle style, Random rng) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String raw = assembleName(style, rng);
            if (isPronounceable(raw) && raw.length() >= MIN_NAME_LENGTH) {
                return capitalize(raw);
            }
        }
        // Ultimate fallback: just return a simple two-syllable name
        return capitalize(assembleName(style, rng));
    }

    private String assembleName(LanguageStyle style, Random rng) {
        int syllables = pickFromArray(style.syllableCount, rng);
        boolean useFrontVowels = chooseVowelHarmony(style, rng);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < syllables; i++) {
            // Onset
            String onset = pickFromArray(style.onsets, rng);
            sb.append(onset);

            // Nucleus (vowel) with harmony
            String nucleus = pickNucleus(style, rng, useFrontVowels);
            sb.append(nucleus);

            // Coda (optional consonant ending)
            if (style.codas.length > 0 && rng.nextFloat() < style.codaChance) {
                sb.append(pickFromArray(style.codas, rng));
            }
        }

        // Suffix
        if (style.suffixes.length > 0 && rng.nextFloat() < style.suffixChance) {
            sb.append(pickFromArray(style.suffixes, rng));
        }

        return sb.toString();
    }

    private boolean chooseVowelHarmony(LanguageStyle style, Random rng) {
        if (style.frontVowels.length == 0 && style.backVowels.length == 0) {
            return rng.nextBoolean(); // no harmony defined, random choice (doesn't affect result)
        }
        return rng.nextBoolean();
    }

    private String pickNucleus(LanguageStyle style, Random rng, boolean useFrontVowels) {
        if (style.frontVowels.length > 0 && style.backVowels.length > 0) {
            if (useFrontVowels) {
                return pickFromArray(style.frontVowels, rng);
            } else {
                return pickFromArray(style.backVowels, rng);
            }
        }
        return pickFromArray(style.nuclei, rng);
    }

    private static String pickFromArray(String[] arr, Random rng) {
        return arr[rng.nextInt(arr.length)];
    }

    private static int pickFromArray(int[] arr, Random rng) {
        return arr[rng.nextInt(arr.length)];
    }

    /**
     * Checks that a name does not contain 4+ consecutive consonant characters.
     */
    static boolean isPronounceable(String name) {
        if (name.length() < MIN_NAME_LENGTH) {
            return false;
        }
        int consecutiveConsonants = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (isVowel(c)) {
                consecutiveConsonants = 0;
            } else if (Character.isLetter(c)) {
                consecutiveConsonants++;
                if (consecutiveConsonants >= MAX_CONSECUTIVE_CONSONANTS) {
                    return false;
                }
            } else {
                // Non-letter chars (hyphens, spaces) reset the count
                consecutiveConsonants = 0;
            }
        }
        return true;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    /** Clears all tracked used names (useful for testing). */
    public void reset() {
        usedNames.clear();
    }

    /** Returns the set of used names for a given category (read-only view for testing). */
    Set<String> getUsedNames(String category) {
        return usedNames.getOrDefault(category, Set.of());
    }
}
