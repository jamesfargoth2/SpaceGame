package com.galacticodyssey.persistence;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailCaptureTest {

    @BeforeAll
    static void loadNatives() {
        new SharedLibraryLoader().load("gdx");
    }


    @Test
    void downscaleReducesResolution() {
        Pixmap source = new Pixmap(1920, 1080, Pixmap.Format.RGBA8888);
        source.setColor(1f, 0f, 0f, 1f);
        source.fill();

        Pixmap result = ThumbnailCapture.downscale(source, 384, 216);

        assertEquals(384, result.getWidth());
        assertEquals(216, result.getHeight());

        result.dispose();
        source.dispose();
    }

    @Test
    void downscaleHandlesAlreadySmallImage() {
        Pixmap source = new Pixmap(200, 100, Pixmap.Format.RGBA8888);
        source.setColor(0f, 1f, 0f, 1f);
        source.fill();

        Pixmap result = ThumbnailCapture.downscale(source, 384, 216);

        assertEquals(384, result.getWidth());
        assertEquals(216, result.getHeight());

        result.dispose();
        source.dispose();
    }
}
