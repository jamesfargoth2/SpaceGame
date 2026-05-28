package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.List;

public class JobBoardTabActor extends Table {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);
    private static final int MAX_ACTIVE_JOBS = 10;

    private final Skin skin;
    private final JobBoard jobBoard;
    private final JobRegistry jobRegistry;
    private final QuestJournal journal;
    private final EventBus eventBus;
    private final ReputationQuery reputation;

    private JobType typeFilter = null;
    private Table listTable;
    private Table detailTable;

    public JobBoardTabActor(Skin skin, JobBoard jobBoard, JobRegistry jobRegistry,
                            QuestJournal journal, EventBus eventBus, ReputationQuery reputation) {
        this.skin = skin;
        this.jobBoard = jobBoard;
        this.jobRegistry = jobRegistry;
        this.journal = journal;
        this.eventBus = eventBus;
        this.reputation = reputation;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();

        if (jobBoard == null) {
            buildUndockedState();
            return;
        }

        buildFilterBar();
        buildContent();
    }

    private void buildUndockedState() {
        Table center = new Table();
        Label icon = new Label("[antenna]", skin, "header");
        icon.setColor(DIM);
        center.add(icon).padBottom(8).row();

        Label msg = new Label("No Station Network", skin, "header");
        msg.setColor(Color.GRAY);
        center.add(msg).padBottom(4).row();

        Label sub = new Label("Dock at a station to browse available jobs", skin, "body");
        sub.setColor(DIM);
        center.add(sub);

        add(center).expand().center();
    }

    private void buildFilterBar() {
        Table filterBar = new Table();
        TextButton allChip = new TextButton("All", skin, "small");
        allChip.getLabel().setColor(typeFilter == null ? CYAN : Color.GRAY);
        allChip.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                typeFilter = null;
                refresh();
            }
        });
        filterBar.add(allChip).padRight(6);

        for (JobType type : JobType.values()) {
            String label = formatTypeName(type);
            TextButton chip = new TextButton(label, skin, "small");
            chip.getLabel().setColor(type == typeFilter ? CYAN : Color.GRAY);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    typeFilter = type;
                    refresh();
                }
            });
            filterBar.add(chip).padRight(6);
        }
        add(filterBar).left().padBottom(8).row();
    }

    private void buildContent() {
        Table splitPane = new Table();

        listTable = new Table();
        listTable.top().left();
        ScrollPane listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        detailTable = new Table();
        detailTable.top().left().pad(12);

        List<JobInstance> allJobs = jobBoard.getAllBoardJobs();
        int shown = 0;
        for (JobInstance job : allJobs) {
            if (typeFilter != null && job.type != typeFilter) continue;

            JobTemplate template = jobRegistry != null ? jobRegistry.get(job.templateId) : null;
            boolean locked = false;
            if (template != null && reputation != null && template.requiredStanding > 0) {
                locked = reputation.getStanding(template.giverFactionTag) < template.requiredStanding;
            }
            addJobRow(job, template, locked);
            shown++;
        }

        if (shown == 0) {
            Label empty = new Label("No jobs available", skin, "body");
            empty.setColor(Color.GRAY);
            listTable.add(empty).pad(20);
        }

        Table header = new Table();
        Label stationLabel = new Label(jobBoard.getStationId(), skin, "slot-name");
        stationLabel.setColor(CYAN);
        header.add(stationLabel).left().expandX();
        Label countLabel = new Label(shown + " available", skin, "slot-detail");
        header.add(countLabel).right();
        add(header).expandX().fillX().padBottom(4).row();

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private void addJobRow(JobInstance job, JobTemplate template, boolean locked) {
        Table row = new Table();
        row.pad(8).left();

        String name = job.displayName != null ? job.displayName
                : (template != null && template.name != null ? template.name : job.templateId);
        Label nameLabel = new Label(name, skin, "body");
        nameLabel.setColor(locked ? DIM : getJobColor(job.type));
        row.add(nameLabel).expandX().left();

        if (locked && template != null) {
            Label lockLabel = new Label("Locked", skin, "slot-detail");
            lockLabel.setColor(RED);
            row.add(lockLabel).right().padLeft(8);
        } else {
            int stars = Math.max(1, Math.min(3, (int) Math.ceil(job.difficulty)));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stars; i++) sb.append("*");
            Label diffLabel = new Label(sb.toString(), skin, "slot-detail");
            diffLabel.setColor(stars >= 3 ? RED : stars >= 2 ? GOLD : GREEN);
            row.add(diffLabel).right().padLeft(8);

            if (job.reward != null && job.reward.credits > 0) {
                Label rewardLabel = new Label(job.reward.credits + " Cr", skin, "slot-detail");
                rewardLabel.setColor(GOLD);
                row.add(rewardLabel).right().padLeft(8);
            }
        }

        if (!locked) {
            TextButton selectBtn = new TextButton(">", skin, "small");
            selectBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    showDetail(job, template);
                }
            });
            row.add(selectBtn).right().padLeft(4);
        }

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(JobInstance job, JobTemplate template) {
        detailTable.clear();

        String name = job.displayName != null ? job.displayName
                : (template != null && template.name != null ? template.name : job.templateId);
        Label nameLabel = new Label(name, skin, "header");
        nameLabel.setColor(getJobColor(job.type));
        detailTable.add(nameLabel).left().padBottom(4).row();

        String desc = job.displayDescription != null ? job.displayDescription
                : (template != null && template.description != null ? template.description : "");
        if (!desc.isEmpty()) {
            Label descLabel = new Label(desc, skin, "body");
            descLabel.setWrap(true);
            descLabel.setColor(Color.LIGHT_GRAY);
            detailTable.add(descLabel).width(280).left().padBottom(12).row();
        }

        int stars = Math.max(1, Math.min(3, (int) Math.ceil(job.difficulty)));
        String[] diffNames = {"Easy", "Medium", "Hard"};
        Label diffHeader = new Label("DIFFICULTY", skin, "slot-name");
        diffHeader.setColor(CYAN);
        detailTable.add(diffHeader).left().padBottom(4).row();
        Label diffLabel = new Label(diffNames[stars - 1], skin, "body");
        diffLabel.setColor(stars >= 3 ? RED : stars >= 2 ? GOLD : GREEN);
        detailTable.add(diffLabel).left().padBottom(8).row();

        if (job.timeLimit > 0) {
            Label timeHeader = new Label("TIME LIMIT", skin, "slot-name");
            timeHeader.setColor(CYAN);
            detailTable.add(timeHeader).left().padBottom(4).row();
            Label timeLabel = new Label(formatTime(job.timeLimit) + " from acceptance", skin, "body");
            detailTable.add(timeLabel).left().padBottom(8).row();
        }

        if (!job.objectives.isEmpty()) {
            Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
            objHeader.setColor(CYAN);
            detailTable.add(objHeader).left().padBottom(4).row();
            for (Objective obj : job.objectives) {
                Label objLabel = new Label("  [ ] " + obj.targetId, skin, "body");
                detailTable.add(objLabel).left().padBottom(2).row();
            }
        }

        if (job.reward != null) {
            Label rewardHeader = new Label("REWARDS", skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padTop(8).padBottom(4).row();
            if (job.reward.credits > 0) {
                Label credLabel = new Label(job.reward.credits + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (job.reward.reputationFaction != null) {
                String sign = job.reward.reputationDelta >= 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) job.reward.reputationDelta + " " + job.reward.reputationFaction, skin, "body");
                repLabel.setColor(GREEN);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        boolean atCap = journal.getActiveJobs().size() >= MAX_ACTIVE_JOBS;
        String btnText = atCap ? MAX_ACTIVE_JOBS + "/" + MAX_ACTIVE_JOBS + " Active" : "ACCEPT JOB";
        TextButton acceptBtn = new TextButton(btnText, skin);
        acceptBtn.setDisabled(atCap);
        if (!atCap) {
            acceptBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    eventBus.publish(new JobAcceptedEvent(job.instanceId));
                    refresh();
                }
            });
        }
        detailTable.add(acceptBtn).expandX().fillX().padTop(16).row();
    }

    private Color getJobColor(JobType type) {
        if (type == null) return CYAN;
        switch (type) {
            case BOUNTY_HUNT:
            case MERCENARY:
                return RED;
            case EXPLORATION_SURVEY:
                return GREEN;
            default:
                return CYAN;
        }
    }

    private String formatTypeName(JobType type) {
        String name = type.name().replace('_', ' ');
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private String formatTime(float seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
