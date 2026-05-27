package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.IntArray;

public class DeckWashComponent implements Component {
    public final IntArray gunwaleSampleIndices = new IntArray();
    public float deckHeight;
    public float gunwaleSegmentLength = 2.0f;
    public float dischargeCd = 0.6f;
    public String topCompartmentId;
    public boolean deckAwash;
    public float deckAwashTimer;
    public static final float DECK_AWASH_THRESHOLD = 3.0f;
}
