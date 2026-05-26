package com.galacticodyssey.galaxy;

public enum LuminosityClass {
    MAIN_SEQUENCE(0.85f),
    GIANT(0.08f),
    SUPERGIANT(0.02f),
    WHITE_DWARF(0.05f);

    public final float frequency;

    LuminosityClass(float frequency) {
        this.frequency = frequency;
    }

    private static final float[] CUMULATIVE;
    static {
        LuminosityClass[] values = values();
        CUMULATIVE = new float[values.length];
        float sum = 0f;
        for (int i = 0; i < values.length; i++) {
            sum += values[i].frequency;
            CUMULATIVE[i] = sum;
        }
    }

    public static LuminosityClass fromRoll(float roll) {
        LuminosityClass[] values = values();
        for (int i = 0; i < CUMULATIVE.length; i++) {
            if (roll < CUMULATIVE[i]) return values[i];
        }
        return WHITE_DWARF;
    }
}
