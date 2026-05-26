package com.galacticodyssey.galaxy;

public enum SpectralClass {
    O(0.01f, 30000f, 50000f, 1, 3, 0.05f),
    B(0.02f, 10000f, 30000f, 1, 4, 0.10f),
    A(0.05f, 7500f, 10000f, 2, 5, 0.20f),
    F(0.08f, 6000f, 7500f, 2, 6, 0.40f),
    G(0.12f, 5200f, 6000f, 3, 6, 0.60f),
    K(0.22f, 3700f, 5200f, 2, 6, 0.45f),
    M(0.50f, 2400f, 3700f, 2, 4, 0.25f);

    public final float frequency;
    public final float tempMin;
    public final float tempMax;
    public final int planetCountMin;
    public final int planetCountMax;
    public final float habitableOdds;

    SpectralClass(float frequency, float tempMin, float tempMax,
                  int planetCountMin, int planetCountMax, float habitableOdds) {
        this.frequency = frequency;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.planetCountMin = planetCountMin;
        this.planetCountMax = planetCountMax;
        this.habitableOdds = habitableOdds;
    }

    private static final float[] CUMULATIVE;
    static {
        SpectralClass[] values = values();
        CUMULATIVE = new float[values.length];
        float sum = 0f;
        for (int i = 0; i < values.length; i++) {
            sum += values[i].frequency;
            CUMULATIVE[i] = sum;
        }
    }

    public static SpectralClass fromRoll(float roll) {
        SpectralClass[] values = values();
        for (int i = 0; i < CUMULATIVE.length; i++) {
            if (roll < CUMULATIVE[i]) return values[i];
        }
        return M;
    }
}
