package com.galacticodyssey.hacking.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.*;
import com.galacticodyssey.hacking.events.*;

public class HackingOverlay implements Disposable {

    private final Stage stage;
    private final Skin skin;
    private HackingController activeController;
    private Table tileTable;
    private Label timerLabel;
    private Label statusLabel;
    private Window window;

    public HackingOverlay(EventBus eventBus, Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());

        eventBus.subscribe(HackSucceededEvent.class, e -> showStatus("ACCESS GRANTED", Color.GREEN));
        eventBus.subscribe(HackFailedEvent.class, e -> showStatus("ACCESS DENIED", Color.RED));
    }

    /** Called by GameScreen after HackStartedEvent fires. */
    public void show(HackingController controller) {
        this.activeController = controller;
        rebuildGrid();
        Gdx.input.setInputProcessor(stage);
    }

    private void rebuildGrid() {
        stage.clear();
        if (activeController == null) return;

        window = new Window("HACKING", skin);
        window.setMovable(false);

        timerLabel = new Label("", skin);
        statusLabel = new Label("", skin);

        tileTable = new Table();
        PuzzleGrid grid = activeController.getGrid();
        int rows = grid.getRows();
        int cols = grid.getCols();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                final int row = r, col = c;
                TextButton btn = new TextButton(tileSymbol(grid.getTile(r, c)), skin);
                btn.getColor().set(grid.getTile(r, c).powered ? Color.GREEN : Color.DARK_GRAY);
                if (grid.getTile(r, c).isSource) btn.getColor().set(Color.YELLOW);
                if (grid.getTile(r, c).isTarget) btn.getColor().set(Color.RED);
                btn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        activeController.rotateTile(row, col);
                        refreshGrid();
                    }
                });
                tileTable.add(btn).size(60, 60).pad(4);
            }
            tileTable.row();
        }

        window.add(timerLabel).colspan(cols).center().padBottom(8).row();
        window.add(tileTable).colspan(cols).row();
        window.add(statusLabel).colspan(cols).center().padTop(8).row();
        window.pack();
        window.setPosition(
            (stage.getWidth() - window.getWidth()) / 2f,
            (stage.getHeight() - window.getHeight()) / 2f);
        stage.addActor(window);
    }

    private void refreshGrid() {
        if (activeController == null) return;
        PuzzleGrid grid = activeController.getGrid();
        int rows = grid.getRows();
        int cols = grid.getCols();
        int btnIdx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GridTile tile = grid.getTile(r, c);
                Cell<?> cell = tileTable.getCells().get(btnIdx++);
                TextButton btn = (TextButton) cell.getActor();
                btn.setText(tileSymbol(tile));
                if (tile.isSource) btn.getColor().set(Color.YELLOW);
                else if (tile.isTarget) btn.getColor().set(Color.RED);
                else btn.getColor().set(tile.powered ? Color.GREEN : Color.DARK_GRAY);
            }
        }
    }

    private void showStatus(String text, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setColor(color);
        }
    }

    public void hide() {
        activeController = null;
        stage.clear();
    }

    public void render(float dt) {
        if (activeController == null) return;
        if (timerLabel != null) {
            timerLabel.setText(String.format("%.1f s", activeController.getTimeRemaining()));
        }
        stage.act(dt);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    private static String tileSymbol(GridTile tile) {
        if (tile.isSource) return "S";
        if (tile.isTarget) return "T";
        switch (tile.type) {
            case STRAIGHT: return tile.rotation % 2 == 0 ? "|" : "-";
            case ELBOW:
                switch (tile.rotation) {
                    case 0: return "L";
                    case 1: return "r";
                    case 2: return "7";
                    default: return "J";
                }
            case TEE:
                switch (tile.rotation) {
                    case 0: return "T";
                    case 1: return "|>";
                    case 2: return "P";
                    default: return "<|";
                }
            case CROSS:  return "+";
            default:     return " ";
        }
    }
}
