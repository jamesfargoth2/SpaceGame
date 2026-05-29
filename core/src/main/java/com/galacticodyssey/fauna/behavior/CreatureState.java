package com.galacticodyssey.fauna.behavior;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.ashley.core.Entity;

public enum CreatureState implements State<Entity> {
    IDLE, WANDER, ALERT, FLEE, HUNT, ATTACK, FEED;

    @Override public void enter(Entity entity) {}
    @Override public void update(Entity entity) {}
    @Override public void exit(Entity entity) {}
    @Override public boolean onMessage(Entity entity, Telegram telegram) { return false; }
}
