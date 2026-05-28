package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.job.JobType;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestAbandonedEvent;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ActiveQuestsTabActor extends Table {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color PURPLE = new Color(0.61f, 0.35f, 0.71f, 1f);
    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);

    private enum QuestFilter { ALL, JOBS, FACTION, COMPANION }

    private final Skin skin;
    private final QuestJournal journal;
    private final EventBus eventBus;

    private QuestFilter currentFilter = QuestFilter.ALL;
    private Table listTable;
    private Table detailTable;
    private ScrollPane listScroll;

    public ActiveQuestsTabActor(Skin skin, QuestJournal journal, EventBus eventBus) {
        this.skin = skin;
        this.journal = journal;
        this.eventBus = eventBus;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        buildFilterBar();
        buildContent();
    }

    private void buildFilterBar() {
        Table filterBar = new Table();
        for (QuestFilter filter : QuestFilter.values()) {
            TextButton chip = new TextButton(filter.name().charAt(0) + filter.name().substring(1).toLowerCase(), skin, "small");
            chip.getLabel().setColor(filter == currentFilter ? CYAN : Color.GRAY);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    currentFilter = filter;
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
        listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        detailTable = new Table();
        detailTable.top().left().pad(12);

        List<QuestEntry> entries = buildQuestEntries();
        entries.sort(Comparator.comparingDouble(e -> e.urgency));

        for (QuestEntry entry : entries) {
            addQuestRow(entry);
        }

        if (entries.isEmpty()) {
            Label empty = new Label("No active quests", skin, "body");
            empty.setColor(Color.GRAY);
            listTable.add(empty).pad(20);
        }

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private List<QuestEntry> buildQuestEntries() {
        List<QuestEntry> entries = new ArrayList<>();

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.JOBS) {
            for (JobInstance job : journal.getActiveJobs()) {
                if (job.state != JobState.ACTIVE) continue;
                entries.add(new QuestEntry(
                    job.displayName != null ? job.displayName : job.templateId,
                    job.type != null ? job.type.name() : "",
                    getJobColor(job.type),
                    job.timeLimit > 0 ? job.timeLimit - job.elapsed : -1,
                    job, null, "JOB"));
            }
        }

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.FACTION) {
            for (SagaInstance saga : journal.getActiveFactionChains()) {
                entries.add(new QuestEntry(
                    saga.sagaDataId, "Faction Chain", PURPLE,
                    -1, null, saga, "FACTION"));
            }
        }

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.COMPANION) {
            for (SagaInstance saga : journal.getActiveCompanionArcs()) {
                entries.add(new QuestEntry(
                    saga.sagaDataId, "Companion Arc", GREEN,
                    -1, null, saga, "COMPANION"));
            }
        }

        return entries;
    }

    private void addQuestRow(QuestEntry entry) {
        Table row = new Table();
        row.pad(8).left();

        Label nameLabel = new Label(entry.name, skin, "body");
        nameLabel.setColor(entry.color);
        row.add(nameLabel).expandX().left();

        if (entry.timeRemaining > 0) {
            String timeStr = formatTime(entry.timeRemaining);
            Label timeLabel = new Label(timeStr, skin, "slot-detail");
            timeLabel.setColor(GOLD);
            row.add(timeLabel).right().padLeft(8);
        }

        TextButton selectBtn = new TextButton(">", skin, "small");
        selectBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDetail(entry);
            }
        });
        row.add(selectBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(QuestEntry entry) {
        detailTable.clear();

        Label nameLabel = new Label(entry.name, skin, "header");
        nameLabel.setColor(entry.color);
        detailTable.add(nameLabel).left().padBottom(4).row();

        Label typeLabel = new Label(entry.typeLabel, skin, "slot-detail");
        detailTable.add(typeLabel).left().padBottom(12).row();

        // Objectives
        List<Objective> objectives = null;
        if (entry.job != null) {
            objectives = entry.job.objectives;
        } else if (entry.saga != null) {
            objectives = entry.saga.activeObjectives;
        }

        if (objectives != null && !objectives.isEmpty()) {
            Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
            objHeader.setColor(CYAN);
            detailTable.add(objHeader).left().padBottom(4).row();

            for (Objective obj : objectives) {
                String prefix = obj.completed ? "  [X] " : "  [ ] ";
                String text = obj.targetId;
                if (obj.requiredCount > 1) {
                    text += " (" + obj.currentCount + "/" + obj.requiredCount + ")";
                }
                Label objLabel = new Label(prefix + text, skin, "body");
                objLabel.setColor(obj.completed ? GREEN : Color.WHITE);
                detailTable.add(objLabel).left().padBottom(2).row();
            }
        }

        // Time remaining
        if (entry.job != null && entry.job.timeLimit > 0) {
            Label timeHeader = new Label("TIME REMAINING", skin, "slot-name");
            timeHeader.setColor(CYAN);
            detailTable.add(timeHeader).left().padTop(8).padBottom(4).row();

            float remaining = entry.job.timeLimit - entry.job.elapsed;
            Label timeLabel = new Label(formatTime(remaining), skin, "body");
            timeLabel.setColor(remaining < 600 ? RED : GOLD);
            detailTable.add(timeLabel).left().padBottom(8).row();
        }

        // Rewards
        if (entry.job != null && entry.job.reward != null) {
            Label rewardHeader = new Label("REWARDS", skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padTop(8).padBottom(4).row();

            if (entry.job.reward.credits > 0) {
                Label credLabel = new Label(entry.job.reward.credits + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (entry.job.reward.reputationFaction != null) {
                String sign = entry.job.reward.reputationDelta >= 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) entry.job.reward.reputationDelta + " " + entry.job.reward.reputationFaction, skin, "body");
                repLabel.setColor(GREEN);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        // Abandon button (jobs only)
        if (entry.category.equals("JOB") && entry.job != null) {
            float repPenalty = 0f;
            if (entry.job.reward != null && entry.job.reward.reputationFaction != null) {
                repPenalty = Math.abs(entry.job.reward.reputationDelta);
            }
            String btnText = repPenalty > 0
                ? "Abandon Quest (-" + (int) repPenalty + " Rep)"
                : "Abandon Quest";
            TextButton abandonBtn = new TextButton(btnText, skin, "small-red");
            abandonBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    eventBus.publish(new QuestAbandonedEvent(entry.job.instanceId));
                    refresh();
                }
            });
            detailTable.add(abandonBtn).left().padTop(16).row();
        }
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

    private String formatTime(float seconds) {
        if (seconds <= 0) return "Expired";
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static class QuestEntry {
        final String name;
        final String typeLabel;
        final Color color;
        final float timeRemaining;
        final JobInstance job;
        final SagaInstance saga;
        final String category;
        final double urgency;

        QuestEntry(String name, String typeLabel, Color color, float timeRemaining,
                   JobInstance job, SagaInstance saga, String category) {
            this.name = name;
            this.typeLabel = typeLabel;
            this.color = color;
            this.timeRemaining = timeRemaining;
            this.job = job;
            this.saga = saga;
            this.category = category;
            this.urgency = timeRemaining > 0 ? timeRemaining : Double.MAX_VALUE;
        }
    }
}
