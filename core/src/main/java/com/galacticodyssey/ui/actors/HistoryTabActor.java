package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.mission.shared.QuestJournal;

public class HistoryTabActor extends Table {
    public HistoryTabActor(Skin skin, QuestJournal journal) {
        add(new Label("History placeholder", skin, "body"));
    }
    public void refresh() {}
}
