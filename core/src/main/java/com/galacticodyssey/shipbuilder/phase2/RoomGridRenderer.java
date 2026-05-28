package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Matrix4;

public class RoomGridRenderer {
    private final ImmediateModeRenderer20 renderer;
    private static final Color GRID_COLOR = new Color(0.3f, 0.8f, 0.8f, 0.15f);

    public RoomGridRenderer() {
        renderer = new ImmediateModeRenderer20(false, true, 0);
    }

    public void render(boolean[][][] hullMask, Camera camera) {
        if (hullMask == null) return;
        int maxX = hullMask.length;
        int maxY = hullMask[0].length;
        int maxZ = hullMask[0][0].length;

        Matrix4 projView = new Matrix4(camera.combined);
        renderer.begin(projView, GL20.GL_LINES);

        for (int x = 0; x < maxX; x++) {
            for (int z = 0; z < maxZ; z++) {
                if (hullMask[x][0][z]) {
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z);
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x + 1, 0, z);

                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z);
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z + 1);
                }
            }
        }
        renderer.end();
    }

    public void dispose() {
        renderer.dispose();
    }
}
