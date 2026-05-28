package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ui.AtmosphericSkyRenderer;

public class ForwardPass implements Disposable {

    public void render(FrameBuffer hdrBuffer, PerspectiveCamera camera,
                       AtmosphericSkyRenderer skyRenderer,
                       Runnable waterRenderer,
                       Runnable particleRenderer) {

        hdrBuffer.begin();

        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 0, 0xFF);
        Gdx.gl.glStencilMask(0x00);
        if (skyRenderer != null) {
            skyRenderer.render(camera);
        }
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);

        if (waterRenderer != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            waterRenderer.run();
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        if (particleRenderer != null) {
            particleRenderer.run();
        }

        hdrBuffer.end();
    }

    @Override
    public void dispose() {}
}
