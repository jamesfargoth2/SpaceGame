package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionRequestedEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;

/**
 * Boarding resolution menu. Shown when a player-aggressor boarding reaches RESOLVING
 * ({@link BoardingResolutionRequestedEvent}); each button publishes a
 * {@link BoardingResolutionChosenEvent}. Hides on {@link BoardingResolvedEvent}.
 * Mirrors {@link VehicleBayPanel}'s Scene2D + EventBus pattern.
 */
public class BoardingResolutionPanel extends Table implements Disposable {

    private final EventBus eventBus;
    private final EventBus.EventListener<BoardingResolutionRequestedEvent> requestedListener;
    private final EventBus.EventListener<BoardingResolvedEvent> resolvedListener;
    private Entity target;

    public BoardingResolutionPanel(Skin skin, EventBus eventBus) {
        this.eventBus = eventBus;
        pad(16);
        setVisible(false);

        Label title = new Label("Boarding Successful", skin, "header");
        add(title).colspan(4).padBottom(12).row();
        add(button(skin, "Hijack", BoardingOutcome.HIJACK)).pad(6);
        add(button(skin, "Scrap", BoardingOutcome.SCRAP)).pad(6);
        add(button(skin, "Ransom", BoardingOutcome.RANSOM)).pad(6);
        add(button(skin, "Tow", BoardingOutcome.TOW)).pad(6);

        requestedListener = e -> show(e.target);
        resolvedListener = e -> hide();
        eventBus.subscribe(BoardingResolutionRequestedEvent.class, requestedListener);
        eventBus.subscribe(BoardingResolvedEvent.class, resolvedListener);
    }

    private TextButton button(Skin skin, String label, BoardingOutcome outcome) {
        TextButton b = new TextButton(label, skin, "default");
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                choose(outcome);
            }
        });
        return b;
    }

    /** Publishes the chosen outcome for the current target and hides the panel. */
    private void choose(BoardingOutcome outcome) {
        if (target == null) return;
        eventBus.publish(new BoardingResolutionChosenEvent(target, outcome));
        hide();
    }

    public void show(Entity target) {
        this.target = target;
        setVisible(true);
    }

    public void hide() {
        this.target = null;
        setVisible(false);
    }

    @Override
    public void dispose() {
        eventBus.unsubscribe(BoardingResolutionRequestedEvent.class, requestedListener);
        eventBus.unsubscribe(BoardingResolvedEvent.class, resolvedListener);
    }
}
