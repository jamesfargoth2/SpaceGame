package com.galacticodyssey.data.names;

/**
 * Configuration for a naming language used by the procedural name generator.
 * Each style defines the phonemic building blocks (onsets, nuclei, codas),
 * syllable-count distribution, optional suffixes, and vowel-harmony groups.
 */
public final class LanguageStyle {

    public final String id;
    public final String[] onsets;
    public final String[] nuclei;
    public final String[] codas;
    public final float codaChance;
    public final int[] syllableCount;
    public final String[] suffixes;
    public final float suffixChance;
    public final String[] frontVowels;
    public final String[] backVowels;

    private LanguageStyle(Builder builder) {
        this.id = builder.id;
        this.onsets = builder.onsets;
        this.nuclei = builder.nuclei;
        this.codas = builder.codas;
        this.codaChance = builder.codaChance;
        this.syllableCount = builder.syllableCount;
        this.suffixes = builder.suffixes;
        this.suffixChance = builder.suffixChance;
        this.frontVowels = builder.frontVowels;
        this.backVowels = builder.backVowels;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String[] onsets = {""};
        private String[] nuclei = {"a"};
        private String[] codas = {};
        private float codaChance = 0f;
        private int[] syllableCount = {2};
        private String[] suffixes = {};
        private float suffixChance = 0f;
        private String[] frontVowels = {};
        private String[] backVowels = {};

        private Builder(String id) {
            this.id = id;
        }

        public Builder setOnsets(String... onsets) {
            this.onsets = onsets;
            return this;
        }

        public Builder setNuclei(String... nuclei) {
            this.nuclei = nuclei;
            return this;
        }

        public Builder setCodas(String... codas) {
            this.codas = codas;
            return this;
        }

        public Builder setCodaChance(float codaChance) {
            this.codaChance = codaChance;
            return this;
        }

        public Builder setSyllableCount(int... syllableCount) {
            this.syllableCount = syllableCount;
            return this;
        }

        public Builder setSuffixes(String... suffixes) {
            this.suffixes = suffixes;
            return this;
        }

        public Builder setSuffixChance(float suffixChance) {
            this.suffixChance = suffixChance;
            return this;
        }

        public Builder setFrontVowels(String... frontVowels) {
            this.frontVowels = frontVowels;
            return this;
        }

        public Builder setBackVowels(String... backVowels) {
            this.backVowels = backVowels;
            return this;
        }

        public LanguageStyle build() {
            return new LanguageStyle(this);
        }
    }
}
