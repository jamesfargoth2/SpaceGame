package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;

/** Resolves an authored .g3db part model by reference. */
public final class AuthoredPartProvider implements PartGeometryProvider {
    private final AssetManager assets;

    public AuthoredPartProvider(AssetManager assets) { this.assets = assets; }

    @Override public boolean supports(PartGeometrySpec spec) {
        return spec.kind == PartGeometrySpec.Kind.AUTHORED && spec.modelRef != null;
    }

    @Override public Model buildPartModel(PartGeometrySpec spec) {
        if (!assets.isLoaded(spec.modelRef)) {
            assets.load(spec.modelRef, Model.class);
            assets.finishLoadingAsset(spec.modelRef);
        }
        return assets.get(spec.modelRef, Model.class);
    }

    @Override public void dispose() { /* models owned by AssetManager */ }
}
