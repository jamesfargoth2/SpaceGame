package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.mission.shared.QuestJournal;

public class StoryTabActor extends Table {
    public StoryTabActor(Skin skin, QuestJournal journal, SagaRegistry sagaRegistry, EventBus eventBus) {
        add(new Label("Story tab placeholder", skin, "body"));
    }
    public void refresh() {}
}
