package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.Item;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Renders a slice of an {@link InventoryComponent} grid as a Scene2D {@link Table}.
 *  The grid is split into left (cols 0–4) and right (cols 5–9) panels that flank the
 *  centre equipment slots. Create one actor per panel with the appropriate startCol/endCol.
 *  Call {@link #initialize()} once a GL context is available, then {@link #refresh(InventoryComponent)}
 *  whenever inventory contents change. */
public class InventoryGridActor extends Table implements Disposable {

    public static final float CELL_SIZE = 56f;
    private static final float CELL_PAD = 2f;

    private final Skin skin;
    private final int startCol;
    private final int endCol;
    private Texture emptyCellTexture;
    private final Map<Integer, Table> cellTables = new HashMap<>();
    private final Map<Integer, Label> cellLabels = new HashMap<>();
    private Consumer<Item> onItemClicked;
    private Consumer<Item> onItemRightClicked;
    private Consumer<Item> onItemHovered;

    /** @param startCol inclusive column index this panel starts at (e.g. 0 for left, 5 for right)
     *  @param endCol   exclusive column index this panel ends at  (e.g. 5 for left, 10 for right) */
    public InventoryGridActor(Skin skin, int startCol, int endCol) {
        this.skin = skin;
        this.startCol = startCol;
        this.endCol = endCol;
    }

    /** Must be called once after a GL context exists. Creates cell background texture. */
    public void initialize() {
        Pixmap pix = new Pixmap((int) CELL_SIZE, (int) CELL_SIZE, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0.12f, 0.12f, 0.15f, 0.7f));
        pix.fill();
        pix.setColor(new Color(0.25f, 0.25f, 0.3f, 1f));
        pix.drawRectangle(0, 0, (int) CELL_SIZE, (int) CELL_SIZE);
        emptyCellTexture = new Texture(pix);
        pix.dispose();
    }

    /** Callback fired when the player left-clicks an item cell. */
    public void setOnItemClicked(Consumer<Item> callback) { this.onItemClicked = callback; }

    /** Callback fired when the player right-clicks an item cell. */
    public void setOnItemRightClicked(Consumer<Item> callback) { this.onItemRightClicked = callback; }

    /** Callback fired when the mouse enters an item cell (for tooltip). */
    public void setOnItemHovered(Consumer<Item> callback) { this.onItemHovered = callback; }

    /** Rebuilds all cell widgets from the current inventory state.
     *  Only columns in [startCol, endCol) are rendered. */
    public void refresh(InventoryComponent inv) {
        clear();
        cellTables.clear();
        cellLabels.clear();

        // Build an anchor map: the first (top-left) cell for each multi-cell item.
        IdentityHashMap<Item, int[]> anchors = new IdentityHashMap<>();
        for (int x = 0; x < inv.gridWidth; x++) {
            for (int y = 0; y < inv.gridHeight; y++) {
                Item item = inv.getItemAt(x, y);
                if (item != null && !anchors.containsKey(item)) {
                    anchors.put(item, new int[]{x, y});
                }
            }
        }

        for (int row = 0; row < inv.gridHeight; row++) {
            for (int col = startCol; col < endCol; col++) {
                int idx = cellIndex(col, row, inv.gridWidth);
                Item item = inv.getItemAt(col, row);
                int[] anchor = item != null ? anchors.get(item) : null;
                boolean isAnchor = anchor != null && anchor[0] == col && anchor[1] == row;

                Table cell = new Table();
                cell.setBackground(new TextureRegionDrawable(new TextureRegion(emptyCellTexture)));

                Label label = new Label("", skin, "body");
                label.setFontScale(0.55f);

                if (item != null && isAnchor) {
                    // Show item name truncated to 7 chars at the anchor cell.
                    label.setText(item.name.length() > 7 ? item.name.substring(0, 7) : item.name);
                    label.setColor(ItemDetailPanel.getQualityColor(item.qualityTier));

                    if (item.stackable && item.currentStack > 1) {
                        Label stackLabel = new Label("x" + item.currentStack, skin, "body");
                        stackLabel.setFontScale(0.5f);
                        stackLabel.setColor(Color.LIGHT_GRAY);
                        cell.add(label).center().row();
                        cell.add(stackLabel).right();
                    } else {
                        cell.add(label).center();
                    }

                    final Item capturedItem = item;
                    cell.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            if (onItemClicked != null) onItemClicked.accept(capturedItem);
                        }

                        @Override
                        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                            super.enter(event, x, y, pointer, fromActor);
                            if (pointer == -1 && onItemHovered != null) onItemHovered.accept(capturedItem);
                        }
                    });
                    cell.addListener(new ClickListener(com.badlogic.gdx.Input.Buttons.RIGHT) {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            if (onItemRightClicked != null) onItemRightClicked.accept(capturedItem);
                        }
                    });
                } else if (item != null) {
                    // Continuation cell of a multi-cell item — slightly dimmed, no label.
                    cell.setColor(new Color(0.15f, 0.15f, 0.18f, 0.5f));
                }

                cellTables.put(idx, cell);
                cellLabels.put(idx, label);
                add(cell).size(CELL_SIZE).pad(CELL_PAD);
            }
            row();
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers (pure math — no GL required, easy to unit-test)
    // -------------------------------------------------------------------------

    /** Converts a grid (x, y) position to a flat cell index. */
    public static int cellIndex(int x, int y, int gridWidth) {
        return y * gridWidth + x;
    }

    /** Converts a flat cell index back to [x, y] grid coordinates. */
    public static int[] cellCoords(int index, int gridWidth) {
        return new int[]{index % gridWidth, index / gridWidth};
    }

    /** Returns true if {@code col} belongs to the left half of a grid of {@code gridWidth}. */
    public static boolean isLeftPanel(int col, int gridWidth) {
        return col < gridWidth / 2;
    }

    // -------------------------------------------------------------------------
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        if (emptyCellTexture != null) {
            emptyCellTexture.dispose();
            emptyCellTexture = null;
        }
    }
}
