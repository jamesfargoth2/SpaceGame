package com.galacticodyssey.hacking.data;

import com.galacticodyssey.hacking.HackEffect;

public class HackableTypeData {
    public String id = "";
    public int difficulty = 1;
    public HackEffect effect = HackEffect.ACCESS_DATA;
    public float lockoutDuration = 45f;
    public boolean requiresPhysicalAccess = true;
    public float interactionRange = 2.5f;
}
