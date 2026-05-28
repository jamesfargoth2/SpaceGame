package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.RoomDesign;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class ShipSchematicRenderer {
    private final ShapeRenderer shapeRenderer;
    private static final float SCALE = 8f;
    private float offsetX, offsetY;

    public ShipSchematicRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void render(ShipDesign design, float x, float y, float width, float height) {
        offsetX = x + 20;
        offsetY = y + 20;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        float hullLength = design.hull.estimateSpineLength() * SCALE;
        float hullWidth = design.hull.estimateMaxWidth() * SCALE;
        shapeRenderer.setColor(0.3f, 0.8f, 0.8f, 0.3f);
        shapeRenderer.ellipse(offsetX, offsetY + height / 2 - hullWidth / 2, hullLength, hullWidth);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (RoomDesign room : design.rooms) {
            Color c = roomColor(room.type);
            shapeRenderer.setColor(c.r, c.g, c.b, 0.6f);
            shapeRenderer.rect(
                offsetX + room.gridX * SCALE,
                offsetY + height / 2 - room.gridZ * SCALE / 2,
                room.sizeX * SCALE,
                room.sizeZ * SCALE
            );
        }
        shapeRenderer.end();
    }

    private Color roomColor(RoomType type) {
        switch (type) {
            case COCKPIT: return Color.CYAN;
            case ENGINE_ROOM: return Color.ORANGE;
            case CREW_QUARTERS: return Color.YELLOW;
            case MEDBAY: return new Color(0.6f, 0.6f, 1f, 1f);
            case ARMORY: return Color.RED;
            case CARGO_BAY: return Color.GRAY;
            // No BRIG — RoomType enum doesn't have it
            default: return Color.WHITE;
        }
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
