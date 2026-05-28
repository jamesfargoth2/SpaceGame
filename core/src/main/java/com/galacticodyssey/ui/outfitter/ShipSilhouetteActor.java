package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.ship.modules.ModuleSlotType;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;

import java.util.function.Consumer;

public class ShipSilhouetteActor extends Widget {

    private static final Color HULL_FILL = new Color(0.07f, 0.09f, 0.14f, 0.8f);
    private static final Color HULL_STROKE = new Color(0.23f, 0.51f, 0.96f, 1f);
    private static final Color WEAPON_SLOT_COLOR = new Color(0.96f, 0.62f, 0.04f, 0.9f);
    private static final Color CORE_MODULE_COLOR = new Color(0.13f, 0.83f, 0.93f, 1f);
    private static final Color OPTIONAL_SLOT_COLOR = new Color(0.28f, 0.33f, 0.41f, 1f);
    private static final Color ENGINE_GLOW = new Color(0.98f, 0.45f, 0.09f, 0.6f);
    private static final Color SELECTED_COLOR = new Color(0.4f, 0.8f, 1f, 1f);

    private ShapeRenderer shapeRenderer;
    private ShipModuleRegistry.SlotLayout slotLayout;
    private ShipLoadoutComponent loadout;
    private ShipHardpointComponent hardpoints;
    private String selectedSlotId;
    private boolean showWeaponSlots = true;
    private boolean showModuleSlots = true;

    private Consumer<String> onSlotClicked;
    private float pulseTimer;

    public ShipSilhouetteActor() {
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleClick(x, y);
            }
        });
    }

    public void initialize() {
        shapeRenderer = new ShapeRenderer();
    }

    public void setData(ShipModuleRegistry.SlotLayout layout,
                        ShipLoadoutComponent loadout,
                        ShipHardpointComponent hardpoints) {
        this.slotLayout = layout;
        this.loadout = loadout;
        this.hardpoints = hardpoints;
    }

    public void setOnSlotClicked(Consumer<String> callback) { this.onSlotClicked = callback; }
    public void setSelectedSlotId(String id) { this.selectedSlotId = id; }
    public void setShowWeaponSlots(boolean show) { this.showWeaponSlots = show; }
    public void setShowModuleSlots(boolean show) { this.showModuleSlots = show; }

    @Override
    public void act(float delta) {
        super.act(delta);
        pulseTimer += delta * 3f;
    }

    @Override
    public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
        if (slotLayout == null) return;

        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.setTransformMatrix(batch.getTransformMatrix());

        float ox = getX();
        float oy = getY();
        float w = getWidth();
        float h = getHeight();
        float scale = Math.min(w / 200f, h / 360f);
        float cx = ox + w / 2f;
        float cy = oy + h / 2f;

        drawHull(cx, cy, scale);
        if (showModuleSlots) drawModuleSlots(cx, cy, scale);
        if (showWeaponSlots) drawHardpointSlots(cx, cy, scale);

        batch.begin();
    }

    private void drawHull(float cx, float cy, float scale) {
        if (slotLayout.silhouettePoints == null) return;
        float[] pts = slotLayout.silhouettePoints;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HULL_FILL);
        for (int i = 0; i < pts.length - 2; i += 2) {
            float x0 = cx + (pts[0] - 100) * scale;
            float y0 = cy + (170 - pts[1]) * scale;
            float x1 = cx + (pts[i] - 100) * scale;
            float y1 = cy + (170 - pts[i + 1]) * scale;
            float x2 = cx + (pts[i + 2] - 100) * scale;
            float y2 = cy + (170 - pts[i + 3]) * scale;
            shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
        }
        shapeRenderer.end();

        if (slotLayout.wingPolygons != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(HULL_FILL);
            for (float[] wing : slotLayout.wingPolygons) {
                for (int i = 0; i < wing.length - 2; i += 2) {
                    float x0 = cx + (wing[0] - 100) * scale;
                    float y0 = cy + (170 - wing[1]) * scale;
                    float x1 = cx + (wing[i] - 100) * scale;
                    float y1 = cy + (170 - wing[i + 1]) * scale;
                    float x2 = cx + (wing[i + 2] - 100) * scale;
                    float y2 = cy + (170 - wing[i + 3]) * scale;
                    shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
                }
            }
            shapeRenderer.end();
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(HULL_STROKE);
        for (int i = 0; i < pts.length - 2; i += 2) {
            float x1 = cx + (pts[i] - 100) * scale;
            float y1 = cy + (170 - pts[i + 1]) * scale;
            float x2 = cx + (pts[i + 2] - 100) * scale;
            float y2 = cy + (170 - pts[i + 3]) * scale;
            shapeRenderer.line(x1, y1, x2, y2);
        }
        float lx = cx + (pts[pts.length - 2] - 100) * scale;
        float ly = cy + (170 - pts[pts.length - 1]) * scale;
        float fx = cx + (pts[0] - 100) * scale;
        float fy = cy + (170 - pts[1]) * scale;
        shapeRenderer.line(lx, ly, fx, fy);
        shapeRenderer.end();

        if (slotLayout.engineGlows != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(ENGINE_GLOW);
            for (int i = 0; i < slotLayout.engineGlows.length; i += 2) {
                float ex = cx + (slotLayout.engineGlows[i] - 100) * scale;
                float ey = cy + (170 - slotLayout.engineGlows[i + 1]) * scale;
                shapeRenderer.ellipse(ex - 6 * scale, ey - 3 * scale, 12 * scale, 6 * scale);
            }
            shapeRenderer.end();
        }
    }

    private void drawModuleSlots(float cx, float cy, float scale) {
        if (loadout == null) return;
        for (ShipModuleSlot slot : loadout.moduleSlots) {
            float sx = cx + (slot.position.x - 100) * scale;
            float sy = cy + (170 - slot.position.y) * scale;
            float slotW = 30 * scale;
            float slotH = 16 * scale;

            boolean selected = slot.id.equals(selectedSlotId);
            Color color = slot.mandatory ? CORE_MODULE_COLOR : OPTIONAL_SLOT_COLOR;
            if (selected) {
                float pulse = 0.7f + 0.3f * (float) Math.sin(pulseTimer);
                color = new Color(SELECTED_COLOR.r, SELECTED_COLOR.g, SELECTED_COLOR.b, pulse);
            }

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(new Color(0.07f, 0.09f, 0.14f, 0.9f));
            shapeRenderer.rect(sx - slotW / 2, sy - slotH / 2, slotW, slotH);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(color);
            shapeRenderer.rect(sx - slotW / 2, sy - slotH / 2, slotW, slotH);
            shapeRenderer.end();
        }
    }

    private void drawHardpointSlots(float cx, float cy, float scale) {
        if (hardpoints == null) return;
        for (Hardpoint hp : hardpoints.hardpoints) {
            float hx = cx + hp.position.x * scale;
            float hy = cy + (170 - hp.position.z * 50) * scale;
            float radius = 8 * scale;

            boolean selected = hp.id.equals(selectedSlotId);
            Color color = selected
                ? new Color(SELECTED_COLOR.r, SELECTED_COLOR.g, SELECTED_COLOR.b,
                            0.7f + 0.3f * (float) Math.sin(pulseTimer))
                : WEAPON_SLOT_COLOR;

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(new Color(0.07f, 0.09f, 0.14f, 0.9f));
            shapeRenderer.circle(hx, hy, radius);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(color);
            shapeRenderer.circle(hx, hy, radius);
            shapeRenderer.end();
        }
    }

    private void handleClick(float x, float y) {
        if (slotLayout == null) return;
        float w = getWidth();
        float h = getHeight();
        float scale = Math.min(w / 200f, h / 360f);
        float cx = w / 2f;
        float cy = h / 2f;

        if (showModuleSlots && loadout != null) {
            for (ShipModuleSlot slot : loadout.moduleSlots) {
                float sx = cx + (slot.position.x - 100) * scale;
                float sy = cy + (170 - slot.position.y) * scale;
                if (Math.abs(x - sx) < 20 * scale && Math.abs(y - sy) < 12 * scale) {
                    if (onSlotClicked != null) onSlotClicked.accept(slot.id);
                    return;
                }
            }
        }

        if (showWeaponSlots && hardpoints != null) {
            for (Hardpoint hp : hardpoints.hardpoints) {
                float hx = cx + hp.position.x * scale;
                float hy = cy + (170 - hp.position.z * 50) * scale;
                float dist = Vector2.dst(x, y, hx, hy);
                if (dist < 12 * scale) {
                    if (onSlotClicked != null) onSlotClicked.accept(hp.id);
                    return;
                }
            }
        }
    }

    public void dispose() {
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
