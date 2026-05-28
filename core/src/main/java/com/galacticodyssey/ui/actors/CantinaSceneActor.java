package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Disposable;

public class CantinaSceneActor extends Group implements Disposable {

    private Texture backgroundTexture;

    public CantinaSceneActor(float width, float height) {
        setSize(width, height);
        createPlaceholderBackground();
    }

    private void createPlaceholderBackground() {
        int w = 256;
        int h = 144;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        for (int y = 0; y < h; y++) {
            float t = (float) y / h;
            int r = (int) (13 + t * 20);
            int g = (int) (17 + t * 10);
            int b = (int) (30 + t * 15);
            pm.setColor(r / 255f, g / 255f, b / 255f, 1f);
            pm.drawLine(0, y, w - 1, y);
        }

        // Bar counter line
        int barY = (int) (h * 0.7f);
        pm.setColor(0.25f, 0.22f, 0.2f, 1f);
        pm.drawLine(w / 10, barY, w * 6 / 10, barY);
        pm.drawLine(w / 10, barY + 1, w * 6 / 10, barY + 1);

        // Ceiling light spots
        pm.setColor(0.9f, 0.27f, 0.37f, 0.12f);
        pm.fillCircle(w / 4, 5, 20);
        pm.setColor(0.2f, 1f, 0.47f, 0.08f);
        pm.fillCircle(w * 3 / 5, 5, 20);

        backgroundTexture = new Texture(pm);
        pm.dispose();

        Image bgImage = new Image(new TextureRegion(backgroundTexture));
        bgImage.setSize(getWidth(), getHeight());
        addActor(bgImage);
    }

    @Override
    public void dispose() {
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
    }
}
