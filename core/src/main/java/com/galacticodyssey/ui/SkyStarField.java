package com.galacticodyssey.ui;

import com.galacticodyssey.galaxy.GalaxyManager;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.galaxy.SpectralClass;
import com.galacticodyssey.galaxy.StarPosition;
import com.galacticodyssey.galaxy.StellarEvolution;
import com.badlogic.gdx.graphics.Color;

import java.util.Random;

public final class SkyStarField {

    public static final int MAX_STARS = 128;

    private final float[] directions = new float[MAX_STARS * 3];
    private final float[] colors = new float[MAX_STARS * 3];
    private final float[] brightnesses = new float[MAX_STARS];
    private int count;

    public void collect(GalaxyManager galaxy, StarPosition playerStar, long galaxySeed) {
        count = 0;
        if (galaxy == null || playerStar == null) return;

        galaxy.updateView(playerStar.x, playerStar.y, 200f);

        double px = playerStar.x;
        double py = playerStar.y;
        double pz = playerStar.z;

        float[] distSq = new float[MAX_STARS];
        for (int i = 0; i < MAX_STARS; i++) distSq[i] = Float.MAX_VALUE;

        for (StarPosition star : galaxy.getLoadedStars()) {
            if (star.uniqueId == playerStar.uniqueId) continue;

            double dx = star.x - px;
            double dy = star.y - py;
            double dz = star.z - pz;
            float d2 = (float)(dx * dx + dy * dy + dz * dz);
            if (d2 < 0.001f) continue;

            int insertIdx = -1;
            for (int i = count - 1; i >= 0; i--) {
                if (d2 < distSq[i]) insertIdx = i;
                else break;
            }
            if (insertIdx < 0 && count < MAX_STARS) insertIdx = count;
            if (insertIdx < 0) continue;

            if (count < MAX_STARS) count++;
            for (int i = count - 1; i > insertIdx; i--) {
                distSq[i] = distSq[i - 1];
                System.arraycopy(directions, (i - 1) * 3, directions, i * 3, 3);
                System.arraycopy(colors, (i - 1) * 3, colors, i * 3, 3);
                brightnesses[i] = brightnesses[i - 1];
            }

            float dist = (float) Math.sqrt(d2);
            distSq[insertIdx] = d2;

            directions[insertIdx * 3]     = (float)(dx / dist);
            directions[insertIdx * 3 + 1] = (float)(dz / dist);
            directions[insertIdx * 3 + 2] = (float)(dy / dist);

            long starSeed = SeedDeriver.forId(
                SeedDeriver.domain(galaxySeed, SeedDeriver.STAR_DOMAIN), star.uniqueId);
            Random rng = new Random(starSeed);
            SpectralClass spectral = SpectralClass.fromRoll(rng.nextFloat());
            float temp = spectral.tempMin + rng.nextFloat() * (spectral.tempMax - spectral.tempMin);
            Color c = StellarEvolution.colorFromTemperature(temp);

            colors[insertIdx * 3]     = c.r;
            colors[insertIdx * 3 + 1] = c.g;
            colors[insertIdx * 3 + 2] = c.b;

            float luminosity = spectral == SpectralClass.O ? 50f :
                               spectral == SpectralClass.B ? 15f :
                               spectral == SpectralClass.A ? 5f :
                               spectral == SpectralClass.F ? 2f :
                               spectral == SpectralClass.G ? 1f :
                               spectral == SpectralClass.K ? 0.4f : 0.1f;
            brightnesses[insertIdx] = luminosity / (d2 + 1f);
        }

        float maxBright = 0f;
        for (int i = 0; i < count; i++) maxBright = Math.max(maxBright, brightnesses[i]);
        if (maxBright > 0f) {
            for (int i = 0; i < count; i++) brightnesses[i] /= maxBright;
        }
    }

    public int getCount() { return count; }
    public float[] getDirections() { return directions; }
    public float[] getColors() { return colors; }
    public float[] getBrightnesses() { return brightnesses; }
}
