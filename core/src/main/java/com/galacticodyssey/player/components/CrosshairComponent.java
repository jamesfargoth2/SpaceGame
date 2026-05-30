package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class CrosshairComponent implements Component {
    public float baseSize = 8f;
    public float currentBloom;
    public float bloomPerShot = 1f;
    public float bloomDecayRate = 12.5f;
    public float hitMarkerTimer;
    public float hitMarkerDuration = 0.2f;
    public float killConfirmTimer;
    public float killConfirmDuration = 0.4f;

    public float getCurrentSize(float weaponSpread) {
        return baseSize + weaponSpread + currentBloom;
    }
}
