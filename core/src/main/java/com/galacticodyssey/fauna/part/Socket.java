package com.galacticodyssey.fauna.part;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** A named attachment point on a part: local transform, accepted child type, symmetry & joint hints. */
public final class Socket {
    public String id;
    public PartType acceptedType;
    public final Vector3 localPosition = new Vector3();
    public final Quaternion localRotation = new Quaternion();
    /** Sockets sharing a non-null group are mirrored as a left/right pair by the assembler. */
    public String mirrorGroup = null;
    /** Joint metadata consumed by Cycle B's rig; ignored in Cycle A. */
    public String jointHint = null;
}
