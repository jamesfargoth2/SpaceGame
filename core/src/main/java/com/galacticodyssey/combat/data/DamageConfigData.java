package com.galacticodyssey.combat.data;

import java.util.HashMap;
import java.util.Map;

public class DamageConfigData {
    public Map<String, Float> hitRegionMultipliers = new HashMap<>();
    public float maxArmorResistance = 0.85f;
    public float defaultShieldRechargeDelay = 4.0f;
    public float wrongBlockMitigation = 0.3f;
    public float exhaustionAttackPenalty = 0.4f;
    public float comboDamageBonus = 0.1f;
    public float comboStaminaPenalty = 0.2f;
    public int maxComboHits = 3;
    public float quickMeleeDamage = 15f;
    public float quickMeleeCooldown = 0.8f;
    public float quickMeleeStaggerDuration = 0.3f;
}
