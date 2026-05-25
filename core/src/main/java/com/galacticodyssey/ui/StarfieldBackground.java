package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

public class StarfieldBackground implements Disposable {

    private static final int LAYER_COUNT = 3;
    private static final int[] COUNTS = {200, 100, 40};
    private static final float[] SPEEDS = {0.5f, 1.5f, 3.0f};
    private static final float[] MIN_SIZES = {1f, 2f, 3f};
    private static final float[] MAX_SIZES = {2f, 3f, 5f};
    private static final float[] MIN_BRIGHTS = {0.3f, 0.5f, 0.7f};
    private static final float[] MAX_BRIGHTS = {0.6f, 0.8f, 1.0f};

    private final float[][] starX;
    private final float[][] starY;
    private final float[][] starSize;
    private final float[][] starBrightness;

    private final Texture pixelTexture;
    private final Texture nebulaTexture;

    private float nebulaX;
    private float nebulaY;
    private float nebulaRotation;
    private float elapsedTime;
    private float worldWidth;
    private float worldHeight;

    public StarfieldBackground(float width, float height) {
        this.worldWidth = width;
        this.worldHeight = height;

        pixelTexture = createPixelTexture();
        nebulaTexture = createNebulaTexture();

        MathUtils.random.setSeed(42L);

        starX = new float[LAYER_COUNT][];
        starY = new float[LAYER_COUNT][];
        starSize = new float[LAYER_COUNT][];
        starBrightness = new float[LAYER_COUNT][];

        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            int count = COUNTS[layer];
            starX[layer] = new float[count];
            starY[layer] = new float[count];
            starSize[layer] = new float[count];
            starBrightness[layer] = new float[count];
            for (int i = 0; i < count; i++) {
                starX[layer][i] = MathUtils.random(0f, width);
                starY[layer][i] = MathUtils.random(0f, height);
                starSize[layer][i] = MathUtils.random(MIN_SIZES[layer], MAX_SIZES[layer]);
                starBrightness[layer][i] = MathUtils.random(MIN_BRIGHTS[layer], MAX_BRIGHTS[layer]);
            }
        }

        nebulaX = width * 0.7f;
        nebulaY = height * 0.4f;
        nebulaRotation = 0f;
    }

    public void resize(float width, float height) {
        this.worldWidth = width;
        this.worldHeight = height;
    }

    public void update(float delta) {
        elapsedTime += delta;

        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            float speed = SPEEDS[layer];
            float dx = speed * delta;
            float dy = -speed * 0.5f * delta;
            for (int i = 0; i < COUNTS[layer]; i++) {
                starX[layer][i] += dx;
                starY[layer][i] += dy;
                if (starX[layer][i] > worldWidth) starX[layer][i] -= worldWidth;
                if (starX[layer][i] < 0) starX[layer][i] += worldWidth;
                if (starY[layer][i] < 0) starY[layer][i] += worldHeight;
                if (starY[layer][i] > worldHeight) starY[layer][i] -= worldHeight;
            }
        }

        nebulaX += 0.3f * delta;
        nebulaY += 0.15f * delta;
        nebulaRotation += 2f * delta;
    }

    public void render(Batch batch) {
        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();

        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        float nebulaSize = Math.min(worldWidth, worldHeight) * 0.55f;
        batch.setColor(0.3f, 0.1f, 0.5f, 0.12f);
        batch.draw(nebulaTexture,
            nebulaX - nebulaSize / 2, nebulaY - nebulaSize / 2,
            nebulaSize / 2, nebulaSize / 2,
            nebulaSize, nebulaSize,
            1f, 1f, nebulaRotation,
            0, 0, nebulaTexture.getWidth(), nebulaTexture.getHeight(),
            false, false);

        batch.setColor(0.1f, 0.15f, 0.4f, 0.1f);
        float nebula2Size = nebulaSize * 1.3f;
        batch.draw(nebulaTexture,
            worldWidth * 0.3f - nebula2Size / 2, worldHeight * 0.6f - nebula2Size / 2,
            nebula2Size / 2, nebula2Size / 2,
            nebula2Size, nebula2Size,
            1f, 1f, -nebulaRotation * 0.7f,
            0, 0, nebulaTexture.getWidth(), nebulaTexture.getHeight(),
            false, false);

        batch.flush();
        batch.setBlendFunction(srcFunc, dstFunc);

        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            for (int i = 0; i < COUNTS[layer]; i++) {
                float alpha = starBrightness[layer][i];
                if (i % 5 == 0) {
                    alpha += 0.2f * MathUtils.sin(elapsedTime * (1.5f + (i % 7) * 0.5f) + i);
                    alpha = MathUtils.clamp(alpha, 0f, 1f);
                }
                batch.setColor(1f, 1f, 1f, alpha);
                float s = starSize[layer][i];
                batch.draw(pixelTexture, starX[layer][i], starY[layer][i], s, s);
            }
        }

        batch.setColor(Color.WHITE);
    }

    private Texture createPixelTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    private Texture createNebulaTexture() {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float center = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / center;
                float alpha = Math.max(0f, 1f - dist);
                alpha *= alpha;
                pixmap.setColor(1f, 1f, 1f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        pixelTexture.dispose();
        nebulaTexture.dispose();
    }
}
