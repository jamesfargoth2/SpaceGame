package com.galacticodyssey.vfx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

public final class ParticleAtlasManager implements Disposable {

    public static final String[] REGIONS = {
        "smoke", "flame", "glow", "flash", "shockwave", "dust", "droplet", "debris_soft"
    };

    private TextureAtlas atlas;

    /** Call once on the GL thread after the OpenGL context is ready. */
    public void generate() {
        PixmapPacker packer = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
        for (String name : REGIONS) {
            Pixmap pm = makeCircle(64, 64);
            packer.pack(name, pm);
            pm.dispose();
        }
        atlas = packer.generateTextureAtlas(TextureFilter.Linear, TextureFilter.Linear, false);
        packer.dispose();
    }

    public TextureRegion getRegion(String name) {
        return atlas != null ? atlas.findRegion(name) : null;
    }

    private static Pixmap makeCircle(int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = w * 0.5f, cy = h * 0.5f;
        float r = Math.min(cx, cy) - 1f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.max(0f, 1f - dist / r);
                int a = (int) (alpha * 255f);
                pm.drawPixel(x, y, (255 << 24) | (255 << 16) | (255 << 8) | a);
            }
        }
        return pm;
    }

    @Override
    public void dispose() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
    }
}
