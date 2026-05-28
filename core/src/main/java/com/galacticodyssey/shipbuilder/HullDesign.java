package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class HullDesign {
    public final List<Vector3> spinePoints = new ArrayList<>();
    public final List<CrossSectionDef> crossSections = new ArrayList<>();
    public final List<AppendageDef> appendages = new ArrayList<>();

    public void addSpinePoint(int index, Vector3 point) {
        spinePoints.add(index, new Vector3(point));
    }

    public void removeSpinePoint(int index) {
        if (spinePoints.size() <= 2) throw new IllegalStateException("Minimum 2 spine points required");
        spinePoints.remove(index);
    }

    public void moveSpinePoint(int index, Vector3 newPosition) {
        spinePoints.get(index).set(newPosition);
    }

    public void addCrossSection(CrossSectionDef def) {
        crossSections.add(def);
        crossSections.sort((a, b) -> Float.compare(a.t, b.t));
    }

    public void removeCrossSection(int index) {
        if (crossSections.size() <= 2) throw new IllegalStateException("Minimum 2 cross-sections required");
        crossSections.remove(index);
    }

    public void addAppendage(AppendageDef def) {
        appendages.add(def);
    }

    public void removeAppendage(int index) {
        appendages.remove(index);
    }

    public float estimateSpineLength() {
        float length = 0;
        for (int i = 1; i < spinePoints.size(); i++) {
            length += spinePoints.get(i).dst(spinePoints.get(i - 1));
        }
        return length;
    }

    public float estimateMaxWidth() {
        float max = 0;
        for (CrossSectionDef cs : crossSections) {
            max = Math.max(max, cs.width);
        }
        return max;
    }

    public float estimateMaxHeight() {
        float max = 0;
        for (CrossSectionDef cs : crossSections) {
            max = Math.max(max, cs.height);
        }
        return max;
    }

    public int countWingPairs() {
        int count = 0;
        for (AppendageDef a : appendages) {
            if (a.type == AppendageDef.AppendageType.SWEPT_WING
                || a.type == AppendageDef.AppendageType.DELTA_WING
                || a.type == AppendageDef.AppendageType.STRAIGHT_WING) {
                count++;
            }
        }
        return count;
    }

    public int countEnginePods() {
        int count = 0;
        for (AppendageDef a : appendages) {
            if (a.type == AppendageDef.AppendageType.ENGINE_POD) count++;
        }
        return count;
    }

    public HullDesign copy() {
        HullDesign copy = new HullDesign();
        for (Vector3 p : spinePoints) copy.spinePoints.add(new Vector3(p));
        for (CrossSectionDef cs : crossSections) copy.crossSections.add(cs.copy());
        for (AppendageDef a : appendages) copy.appendages.add(a.copy());
        return copy;
    }
}
