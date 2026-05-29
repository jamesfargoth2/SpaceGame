package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.Matrix4;

public final class Bone {
    public final int index;
    public final int parentIndex;   // -1 for root
    public final BoneRole role;
    public final String name;       // "bone_0", "bone_1", etc.
    public final Matrix4 bindPose = new Matrix4();    // local-space bind transform
    public final Matrix4 currentPose = new Matrix4(); // local-space animated transform (mutated by gait)

    public Bone(int index, int parentIndex, BoneRole role, String name, Matrix4 bindPose) {
        this.index = index;
        this.parentIndex = parentIndex;
        this.role = role;
        this.name = name;
        this.bindPose.set(bindPose);
        this.currentPose.set(bindPose);
    }

    public boolean isRoot() { return parentIndex < 0; }
}
