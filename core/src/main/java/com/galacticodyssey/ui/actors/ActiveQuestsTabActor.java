package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.shared.QuestJournal;

public class ActiveQuestsTabActor extends Table {
    public ActiveQuestsTabActor(Skin skin, QuestJournal journal, EventBus eventBus) {
        add(new Label("Active quests placeholder", skin, "body"));
    }
    public void refresh() {}
}
