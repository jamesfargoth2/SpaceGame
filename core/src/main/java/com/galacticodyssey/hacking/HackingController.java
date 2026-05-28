package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;

import java.util.Random;

public class HackingController {

    public enum State { IDLE, ACTIVE, SUCCESS, FAILED }

    private State state = State.ACTIVE;
    private float timeRemaining;
    private final PuzzleGrid grid;
    private final EventBus eventBus;
    private final Entity player;
    private final Entity target;
    private final HackEffect effect;

    public HackingController(EventBus eventBus, Entity player, Entity target,
                             HackableComponent hackable, int hackingSkill, boolean remoteHack) {
        this.eventBus = eventBus;
        this.player = player;
        this.target = target;
        this.effect = hackable.effect;

        int effectiveDifficulty = Math.min(5, hackable.difficulty + (remoteHack ? 1 : 0));
        int gridSize = gridSizeFor(effectiveDifficulty);
        this.grid = PuzzleGridFactory.create(gridSize, effectiveDifficulty, hackingSkill, new Random());

        float base = baseTime(effectiveDifficulty);
        if (hackingSkill < hackable.difficulty) base -= 5f;
        if (remoteHack) base -= 10f;
        this.timeRemaining = Math.max(5f, base);
    }

    private static int gridSizeFor(int difficulty) {
        if (difficulty <= 2) return 3;
        if (difficulty <= 4) return 4;
        return 5;
    }

    private static float baseTime(int difficulty) {
        switch (difficulty) {
            case 1: return 30f;
            case 2: return 20f;
            case 3: return 30f;
            case 4: return 20f;
            default: return 25f;
        }
    }

    public void tick(float dt) {
        if (state != State.ACTIVE) return;
        timeRemaining -= dt;
        if (timeRemaining <= 0f) {
            timeRemaining = 0f;
            state = State.FAILED;
            eventBus.publish(new HackFailedEvent(player, target));
        }
    }

    public void rotateTile(int row, int col) {
        if (state != State.ACTIVE) return;
        grid.rotateTile(row, col);
        if (grid.isWon()) {
            state = State.SUCCESS;
            eventBus.publish(new HackSucceededEvent(player, target, effect));
        }
    }

    public void cancel() {
        if (state == State.ACTIVE) state = State.FAILED;
    }

    public State getState() { return state; }
    public float getTimeRemaining() { return timeRemaining; }
    public PuzzleGrid getGrid() { return grid; }
    public HackEffect getEffect() { return effect; }
}
