package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.mission.shared.QuestJournal;

public class RumoursTabActor extends Table {
    public RumoursTabActor(Skin skin, QuestJournal journal) {
        add(new Label("Rumours placeholder", skin, "body"));
    }
    public void refresh() {}
}
