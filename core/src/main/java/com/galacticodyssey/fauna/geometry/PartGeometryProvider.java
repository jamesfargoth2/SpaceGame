package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Disposable;

/** Produces a (single-node) Model for a part's geometry spec. GL-side. */
public interface PartGeometryProvider extends Disposable {
    boolean supports(PartGeometrySpec spec);
    /** Build a Model whose root node mesh is the part, origin at the part's socket origin. */
    Model buildPartModel(PartGeometrySpec spec);
}
