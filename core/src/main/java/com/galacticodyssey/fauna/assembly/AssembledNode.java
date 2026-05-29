package com.galacticodyssey.fauna.assembly;

import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.fauna.part.CreaturePartDef;

import java.util.ArrayList;
import java.util.List;

/** A placed part instance in the assembled creature tree. */
public final class AssembledNode {
    public CreaturePartDef part;
    public final Matrix4 localTransform = new Matrix4();  // relative to parent node
    public final Matrix4 worldTransform = new Matrix4();  // relative to creature root
    public float scale = 1f;
    public boolean mirrored = false;
    public final List<AssembledNode> children = new ArrayList<>();
}
