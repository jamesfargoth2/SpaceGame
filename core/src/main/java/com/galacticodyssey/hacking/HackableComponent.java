package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Component;

public class HackableComponent implements Component {
    public String typeId = "";
    public int difficulty = 1;
    public HackEffect effect = HackEffect.ACCESS_DATA;
    public float lockoutDuration = 45f;
    public float lockoutTimer = 0f;
    public float effectTimer = 0f;
    public boolean requiresPhysicalAccess = true;
    public float interactionRange = 2.5f;
    public boolean unlocked = false;    // set true on UNLOCK effect
    public String terminalId = "";      // used by ACCESS_DATA effect
}
