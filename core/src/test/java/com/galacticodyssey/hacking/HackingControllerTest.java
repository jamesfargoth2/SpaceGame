package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HackingControllerTest {

    private HackableComponent hackable(int difficulty) {
        HackableComponent h = new HackableComponent();
        h.difficulty = difficulty;
        h.effect = HackEffect.UNLOCK;
        return h;
    }

    @Test
    void startsInActiveState() {
        HackingController c = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertEquals(HackingController.State.ACTIVE, c.getState());
    }

    @Test
    void timerExpiryPublishesHackFailedEvent() {
        EventBus bus = new EventBus();
        Entity player = new Entity();
        Entity target = new Entity();
        HackingController c = new HackingController(bus, player, target, hackable(1), 1, false);

        HackFailedEvent[] received = {null};
        bus.subscribe(HackFailedEvent.class, e -> received[0] = e);

        c.tick(31f); // difficulty-1 time limit is 30s

        assertEquals(HackingController.State.FAILED, c.getState());
        assertNotNull(received[0]);
        assertSame(player, received[0].player);
        assertSame(target, received[0].target);
    }

    @Test
    void tickDoesNothingAfterFailed() {
        EventBus bus = new EventBus();
        HackingController c = new HackingController(
            bus, new Entity(), new Entity(), hackable(1), 1, false);
        c.tick(31f); // expires

        int[] callCount = {0};
        bus.subscribe(HackFailedEvent.class, e -> callCount[0]++);
        c.tick(5f); // should be no-op

        assertEquals(0, callCount[0]);
    }

    @Test
    void cancelSilentlyTransitionsToFailed() {
        EventBus bus = new EventBus();
        HackingController c = new HackingController(
            bus, new Entity(), new Entity(), hackable(1), 1, false);

        int[] callCount = {0};
        bus.subscribe(HackFailedEvent.class, e -> callCount[0]++);

        c.cancel();

        assertEquals(HackingController.State.FAILED, c.getState());
        assertEquals(0, callCount[0]); // no event published
    }

    @Test
    void winningGridPublishesHackSucceededEvent() {
        EventBus bus = new EventBus();
        Entity player = new Entity();
        Entity target = new Entity();
        HackableComponent h = hackable(1);
        h.effect = HackEffect.ACCESS_DATA;
        HackingController c = new HackingController(bus, player, target, h, 1, false);

        HackSucceededEvent[] received = {null};
        bus.subscribe(HackSucceededEvent.class, e -> received[0] = e);

        // Force-win: replace all tiles with CROSS, preserving isSource/isTarget flags
        // Difficulty 1 always produces a 3x3 grid
        PuzzleGrid grid = c.getGrid();
        int size = 3;
        for (int r = 0; r < size; r++) {
            for (int col = 0; col < size; col++) {
                GridTile existing = grid.getTile(r, col);
                GridTile cross = new GridTile(TileType.CROSS, 0);
                cross.isSource = existing.isSource;
                cross.isTarget = existing.isTarget;
                grid.setTile(r, col, cross);
            }
        }
        c.rotateTile(0, 0); // CROSS rotation 0→1, still all-connected, win detected

        assertEquals(HackingController.State.SUCCESS, c.getState());
        assertNotNull(received[0]);
        assertEquals(HackEffect.ACCESS_DATA, received[0].effect);
        assertSame(player, received[0].player);
    }

    @Test
    void remoteHackAddsDifficultyPenalty() {
        // Remote hack: +1 effective difficulty, -10s time limit
        // Physical diff-1: 3x3, 30s. Remote diff-1: effective diff-2, 20s -10s = 10s.
        HackingController cRemote = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, true);
        HackingController cPhysical = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertTrue(cRemote.getTimeRemaining() < cPhysical.getTimeRemaining());
    }

    @Test
    void puzzleIsNotPreWonWithZeroAssists() {
        // hackingSkill == difficulty → 0 assists → at least 1 tile scrambled → not yet won
        HackingController c = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertFalse(c.getGrid().isWon());
    }
}
