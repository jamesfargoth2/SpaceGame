package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/** A grown branch graph: nodes with positions, parent links, relative radius, tip flags. */
public final class BranchSkeleton {
    private final Array<Vector3> positions = new Array<>();
    private final IntArray parents = new IntArray();
    private float[] relRadius = new float[0];
    private boolean[] tip = new boolean[0];

    int addNode(Vector3 pos, int parent) {
        positions.add(new Vector3(pos));
        parents.add(parent);
        return positions.size - 1;
    }

    void finalizeRadii(float[] relRadius, boolean[] tip) {
        this.relRadius = relRadius;
        this.tip = tip;
    }

    public int size() { return positions.size; }
    public Vector3 position(int i) { return positions.get(i); }
    public int parent(int i) { return parents.get(i); }
    public float relRadius(int i) { return relRadius[i]; }
    public boolean isTip(int i) { return tip[i]; }
}
