package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.saga.SagaData;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.Map;

public class StoryTabActor extends Table {

    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color GREEN = new Color(0.4f, 0.75f, 0.4f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);

    private final Skin skin;
    private final QuestJournal journal;
    private final SagaRegistry sagaRegistry;

    public StoryTabActor(Skin skin, QuestJournal journal, SagaRegistry sagaRegistry, EventBus eventBus) {
        this.skin = skin;
        this.journal = journal;
        this.sagaRegistry = sagaRegistry;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        SagaInstance story = journal.getMainStory();
        if (story == null) {
            buildEmptyState();
            return;
        }
        buildStoryContent(story);
    }

    private void buildEmptyState() {
        Table center = new Table();
        Label msg = new Label("Your story hasn't begun...", skin, "header");
        msg.setColor(DIM);
        center.add(msg);
        add(center).expand().center();
    }

    private void buildStoryContent(SagaInstance story) {
        SagaData sagaData = sagaRegistry != null ? sagaRegistry.get(story.sagaDataId) : null;
        String storyTitle = sagaData != null ? sagaData.title : story.sagaDataId;

        // Two-column layout
        Table leftCol = new Table();
        leftCol.top().left().pad(4);
        Table rightCol = new Table();
        rightCol.top().left().pad(4);

        // Left: Act progression
        buildActProgression(leftCol, story, storyTitle);

        // Right: Choices + rewards
        buildChoicesAndRewards(rightCol, story);

        add(leftCol).expand().fill().padRight(12);
        add(rightCol).width(280).fillY().top();
    }

    private void buildActProgression(Table col, SagaInstance story, String title) {
        Table actCard = new Table();
        actCard.pad(12).left().top();

        Table headerRow = new Table();
        Label titleLabel = new Label(title, skin, "header");
        titleLabel.setColor(GOLD);
        headerRow.add(titleLabel).expandX().left();

        Label statusLabel = new Label("IN PROGRESS", skin, "slot-name");
        statusLabel.setColor(GOLD);
        headerRow.add(statusLabel).right();

        actCard.add(headerRow).expandX().fillX().padBottom(8).row();

        if (story.currentNodeId != null) {
            Label nodeLabel = new Label("Current: " + story.currentNodeId, skin, "slot-name");
            nodeLabel.setColor(CYAN);
            actCard.add(nodeLabel).left().padBottom(8).row();
        }

        // Objectives
        Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
        objHeader.setColor(CYAN);
        actCard.add(objHeader).left().padBottom(4).row();

        for (Objective obj : story.activeObjectives) {
            String prefix = obj.completed ? "  [X] " : "  [ ] ";
            Label objLabel = new Label(prefix + obj.targetId, skin, "body");
            objLabel.setColor(obj.completed ? GREEN : Color.WHITE);
            actCard.add(objLabel).left().padBottom(2).row();
        }

        col.add(actCard).expandX().fillX().row();
    }

    private void buildChoicesAndRewards(Table col, SagaInstance story) {
        if (!story.choicesMade.isEmpty()) {
            Label choicesHeader = new Label("CHOICES MADE", skin, "slot-name");
            choicesHeader.setColor(CYAN);
            col.add(choicesHeader).left().padBottom(6).row();

            for (Map.Entry<String, String> entry : story.choicesMade.entrySet()) {
                Label choiceLabel = new Label(entry.getValue(), skin, "body");
                choiceLabel.setColor(Color.LIGHT_GRAY);
                col.add(choiceLabel).left().padBottom(2).row();

                Label nodeLabel = new Label("Node: " + entry.getKey(), skin, "slot-detail");
                col.add(nodeLabel).left().padBottom(6).row();
            }
        }
    }
}
