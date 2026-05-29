package com.galacticodyssey.fauna.rig;

public enum BoneRole {
    HIP, SHOULDER, NECK, SPINE, TAIL, STRUCTURAL;

    public static BoneRole fromJointHint(String hint) {
        if (hint == null) return STRUCTURAL;
        switch (hint) {
            case "hip":      return HIP;
            case "shoulder": return SHOULDER;
            case "neck":     return NECK;
            case "spine":    return SPINE;
            case "tail":     return TAIL;
            default:         return STRUCTURAL;
        }
    }
}
