package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.List;

public class RumoursTabActor extends Table {

    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);

    private final Skin skin;
    private final QuestJournal journal;

    private String expandedRumourId = null;

    public RumoursTabActor(Skin skin, QuestJournal journal) {
        this.skin = skin;
        this.journal = journal;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        List<JobInstance> rumours = journal.getRumourBoard();

        if (rumours.isEmpty()) {
            Label empty = new Label("No rumours heard yet...", skin, "body");
            empty.setColor(DIM);
            add(empty).expand().center();
            return;
        }

        Label header = new Label("RUMOURS", skin, "slot-name");
        header.setColor(CYAN);
        add(header).left().padBottom(4).row();

        Label countLabel = new Label(rumours.size() + " leads", skin, "slot-detail");
        add(countLabel).left().padBottom(8).row();

        Table listTable = new Table();
        listTable.top().left();
        ScrollPane scroll = new ScrollPane(listTable, skin);
        scroll.setFadeScrollBars(false);

        for (JobInstance rumour : rumours) {
            addRumourRow(listTable, rumour);
        }

        add(scroll).expand().fill();
    }

    private void addRumourRow(Table listTable, JobInstance rumour) {
        Table row = new Table();
        row.pad(8).left();

        Label star = new Label("*", skin, "body");
        star.setColor(GOLD);
        row.add(star).padRight(8);

        String name = rumour.displayName != null ? rumour.displayName : rumour.templateId;
        Label nameLabel = new Label(name, skin, "body");
        nameLabel.setColor(GOLD);
        row.add(nameLabel).expandX().left();

        if (rumour.lead != null && rumour.lead.locationId != null) {
            Label sourceLabel = new Label(rumour.lead.locationId, skin, "slot-detail");
            row.add(sourceLabel).right();
        }

        boolean isExpanded = rumour.instanceId.equals(expandedRumourId);

        TextButton toggleBtn = new TextButton(isExpanded ? "v" : ">", skin, "small");
        toggleBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (rumour.instanceId.equals(expandedRumourId)) {
                    expandedRumourId = null;
                } else {
                    expandedRumourId = rumour.instanceId;
                }
                refresh();
            }
        });
        row.add(toggleBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(2).row();

        if (isExpanded) {
            Table expandedContent = new Table();
            expandedContent.pad(8, 32, 8, 8);

            String desc = rumour.displayDescription != null ? rumour.displayDescription : "Details unknown...";
            Label descLabel = new Label(desc, skin, "body");
            descLabel.setWrap(true);
            descLabel.setColor(Color.LIGHT_GRAY);
            expandedContent.add(descLabel).width(400).left().row();

            if (!rumour.objectives.isEmpty()) {
                Label hintLabel = new Label("Possible objectives:", skin, "slot-detail");
                expandedContent.add(hintLabel).left().padTop(4).row();
                for (Objective obj : rumour.objectives) {
                    Label objLabel = new Label("  - " + obj.targetId, skin, "slot-detail");
                    expandedContent.add(objLabel).left().row();
                }
            }

            listTable.add(expandedContent).expandX().fillX().padBottom(4).row();
        }
    }
}
