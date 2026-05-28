package com.galacticodyssey.flora.data;

import com.badlogic.gdx.graphics.Color;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;

/** Data-driven definition of a flora species. Loaded from data/flora/species.json. */
public class FloraSpecies {
    public String id;
    public String displayName;

    // Envelope
    public EnvelopeShape shape = EnvelopeShape.ELLIPSOID;
    public float heightMin = 6f, heightMax = 10f;
    public float radiusMin = 2f, radiusMax = 3f;

    // Growth (space colonization)
    public int attractionPoints = 160;
    public float influenceRadius = 4f;
    public float killDistance = 0.7f;
    public float segmentLength = 0.45f;
    public int maxNodes = 500;

    // Trunk / branches
    public int trunkSides = 6;
    public float baseRadius = 0.3f;   // radius at the root, in world units
    public float taper = 0.8f;        // 0..1, higher = slower thinning toward tips
    public Color trunkColor = new Color(0.35f, 0.22f, 0.10f, 1f);

    // Foliage
    public FoliageStyle foliageStyle = FoliageStyle.CLUMP;
    public int clumpsPerTip = 1;
    public float clumpRadiusMin = 1.0f, clumpRadiusMax = 1.6f;
    public Color foliageColorA = new Color(0.15f, 0.42f, 0.12f, 1f);
    public Color foliageColorB = new Color(0.20f, 0.50f, 0.15f, 1f);

    // Prototype pool
    public int prototypeVariants = 6;
}
