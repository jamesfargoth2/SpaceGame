package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class ADSComponent implements Component {
    public float adsProgress;
    public float adsSpeed = 5f;
    public float zoomMultiplier = 0.7f;
    public float spreadMultiplier = 0.3f;
    public float moveSpeedMultiplier = 0.6f;
}
