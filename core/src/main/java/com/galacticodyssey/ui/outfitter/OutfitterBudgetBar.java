package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

public class OutfitterBudgetBar extends Table implements Disposable {

    private final Skin skin;
    private Label powerLabel;
    private Label massLabel;
    private Label creditsLabel;
    private Texture barBgTexture;
    private Texture barFillTexture;

    public OutfitterBudgetBar(Skin skin) {
        this.skin = skin;
    }

    public void initialize() {
        Pixmap bg = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bg.setColor(new Color(0.12f, 0.16f, 0.23f, 1f));
        bg.fill();
        barBgTexture = new Texture(bg);
        bg.dispose();

        Pixmap fill = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fill.setColor(new Color(0.96f, 0.62f, 0.04f, 1f));
        fill.fill();
        barFillTexture = new Texture(fill);
        fill.dispose();

        pad(8, 16, 8, 16);
        setBackground(new TextureRegionDrawable(new TextureRegion(barBgTexture)));

        powerLabel = new Label("POWER: 0 / 0 MW", skin);
        powerLabel.setColor(new Color(0.96f, 0.62f, 0.04f, 1f));
        massLabel = new Label("MASS: 0 / 0 t", skin);
        massLabel.setColor(Color.LIGHT_GRAY);
        creditsLabel = new Label("Credits: 0", skin);
        creditsLabel.setColor(new Color(0.98f, 0.75f, 0.15f, 1f));

        add(powerLabel).expandX().left();
        add(massLabel).expandX().center();
        add(creditsLabel).right();
    }

    public void update(float powerDraw, float powerGen, float mass, float maxMass, int credits, boolean stationMode) {
        powerLabel.setText(String.format("POWER: %.0f / %.0f MW", powerDraw, powerGen));
        float powerRatio = powerGen > 0 ? powerDraw / powerGen : 0f;
        powerLabel.setColor(budgetColor(powerRatio));

        massLabel.setText(String.format("MASS: %.1f / %.0f t", mass, maxMass));
        float massRatio = maxMass > 0 ? mass / maxMass : 0f;
        massLabel.setColor(budgetColor(massRatio));

        creditsLabel.setVisible(stationMode);
        if (stationMode) {
            creditsLabel.setText("Credits: " + credits);
        }
    }

    private Color budgetColor(float ratio) {
        if (ratio > 0.85f) return Color.RED;
        if (ratio > 0.6f)  return Color.YELLOW;
        return Color.WHITE;
    }

    @Override
    public void dispose() {
        if (barBgTexture != null) barBgTexture.dispose();
        if (barFillTexture != null) barFillTexture.dispose();
    }
}
