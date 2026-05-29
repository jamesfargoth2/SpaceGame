package com.galacticodyssey.fauna.rig;

import java.util.ArrayList;
import java.util.List;

public final class CreatureRig {
    private final Bone[] bones;

    public CreatureRig(Bone[] bones) {
        this.bones = bones;
    }

    public int boneCount() { return bones.length; }
    public Bone getBone(int index) { return bones[index]; }
    public Bone root() { return bones[0]; }

    public List<Bone> bonesWithRole(BoneRole role) {
        List<Bone> out = new ArrayList<>();
        for (Bone b : bones) if (b.role == role) out.add(b);
        return out;
    }

    public Bone findFirstWithRole(BoneRole role) {
        for (Bone b : bones) if (b.role == role) return b;
        return null;
    }

    public List<Bone> childrenOf(int parentIndex) {
        List<Bone> out = new ArrayList<>();
        for (Bone b : bones) if (b.parentIndex == parentIndex) out.add(b);
        return out;
    }
}
