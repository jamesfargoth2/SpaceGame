package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Disposable;

/** Produces a (single-node) Model for a part's geometry spec. GL-side. */
public interface PartGeometryProvider extends Disposable {
    boolean supports(PartGeometrySpec spec);
    /** Build a Model whose root node mesh is the part, origin at the part's socket origin. */
    Model buildPartModel(PartGeometrySpec spec);

    /** True if the BUILDER owns models returned by buildPartModel and must dispose them.
     *  Procedural providers mint fresh models (true); providers backed by an AssetManager
     *  return shared, manager-owned models (false). */
    default boolean ownsBuiltModels() { return true; }
}
