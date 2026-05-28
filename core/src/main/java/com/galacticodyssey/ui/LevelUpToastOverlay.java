package com.galacticodyssey.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.events.CharacterLevelUpEvent;
import com.galacticodyssey.player.events.PerkAvailableEvent;
import com.galacticodyssey.player.events.SkillLevelUpEvent;

/** Fading top-center notifications for level/skill/perk events. */
public class LevelUpToastOverlay implements Disposable {

    private static final float HOLD = 2.5f;
    private static final float FADE = 0.75f;

    private final Stage stage;
    private final Table root = new Table();
    private final Label label;
    private float timer;

    public LevelUpToastOverlay(EventBus eventBus, Skin skin) {
        stage = new Stage(new ScreenViewport());
        label = new Label("", skin);
        label.setAlignment(Align.center);
        root.setFillParent(true);
        root.top().padTop(80);
        root.add(label);
        root.getColor().a = 0f;
        stage.addActor(root);

        eventBus.subscribe(CharacterLevelUpEvent.class, e ->
            show("LEVEL UP!  Character Level " + e.newLevel + "   +" + e.pointsAwarded + " skill points"));
        eventBus.subscribe(SkillLevelUpEvent.class, e ->
            show(e.skill.name() + "  ->  Lv " + e.newLevel));
        eventBus.subscribe(PerkAvailableEvent.class, e ->
            show("Perk available - open the Character screen"));
    }

    private void show(String text) {
        label.setText(text);
        root.getColor().a = 1f;
        timer = HOLD + FADE;
    }

    public void render(float delta) {
        if (timer > 0f) {
            timer -= delta;
            if (timer < FADE) root.getColor().a = Math.max(0f, timer / FADE);
            stage.act(delta);
            stage.draw();
        }
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() { stage.dispose(); }
}
