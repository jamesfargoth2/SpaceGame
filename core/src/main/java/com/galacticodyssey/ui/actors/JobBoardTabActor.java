package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.mission.shared.QuestJournal;

public class JobBoardTabActor extends Table {
    public JobBoardTabActor(Skin skin, JobBoard jobBoard, JobRegistry jobRegistry,
                            QuestJournal journal, EventBus eventBus, ReputationQuery reputation) {
        add(new Label("Job board placeholder", skin, "body"));
    }
    public void refresh() {}
}
