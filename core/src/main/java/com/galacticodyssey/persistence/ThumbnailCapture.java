package com.galacticodyssey.persistence;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.File;

public class ThumbnailCapture {

    public static final int THUMBNAIL_WIDTH = 384;
    public static final int THUMBNAIL_HEIGHT = 216;

    public static Pixmap captureFramebuffer() {
        return ScreenUtils.getFrameBufferPixmap(
            0, 0,
            com.badlogic.gdx.Gdx.graphics.getBackBufferWidth(),
            com.badlogic.gdx.Gdx.graphics.getBackBufferHeight());
    }

    public static Pixmap downscale(Pixmap source, int targetWidth, int targetHeight) {
        Pixmap target = new Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888);
        target.setFilter(Pixmap.Filter.BiLinear);
        target.drawPixmap(source,
            0, 0, source.getWidth(), source.getHeight(),
            0, 0, targetWidth, targetHeight);
        return target;
    }

    public static void saveThumbnail(Pixmap framebuffer, File saveDir) {
        Pixmap thumbnail = downscale(framebuffer, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        try {
            var thumbFile = new com.badlogic.gdx.files.FileHandle(new File(saveDir, "thumbnail.png"));
            com.badlogic.gdx.graphics.PixmapIO.writePNG(thumbFile, thumbnail);
        } finally {
            thumbnail.dispose();
        }
    }

    public static Pixmap captureAndDownscale() {
        Pixmap full = captureFramebuffer();
        Pixmap thumbnail = downscale(full, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        full.dispose();
        return thumbnail;
    }
}
