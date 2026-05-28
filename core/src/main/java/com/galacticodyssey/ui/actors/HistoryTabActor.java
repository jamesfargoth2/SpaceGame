package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.mission.shared.CompletedQuestRecord;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.shared.QuestOutcome;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HistoryTabActor extends Table {

    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);

    private final Skin skin;
    private final QuestJournal journal;

    private Table listTable;
    private Table detailTable;

    public HistoryTabActor(Skin skin, QuestJournal journal) {
        this.skin = skin;
        this.journal = journal;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();

        List<CompletedQuestRecord> history = journal.getCompletedQuests();

        Table header = new Table();
        Label titleLabel = new Label("HISTORY", skin, "slot-name");
        titleLabel.setColor(CYAN);
        header.add(titleLabel).left().expandX();
        Label countLabel = new Label(history.size() + " total", skin, "slot-detail");
        header.add(countLabel).right();
        add(header).expandX().fillX().padBottom(8).row();

        if (history.isEmpty()) {
            Label empty = new Label("No completed quests yet", skin, "body");
            empty.setColor(DIM);
            add(empty).expand().center();
            return;
        }

        Table splitPane = new Table();

        listTable = new Table();
        listTable.top().left();
        ScrollPane listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        detailTable = new Table();
        detailTable.top().left().pad(12);

        for (CompletedQuestRecord record : history) {
            addHistoryRow(record);
        }

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private void addHistoryRow(CompletedQuestRecord record) {
        Table row = new Table();
        row.pad(8).left();

        String icon;
        Color iconColor;
        switch (record.outcome) {
            case COMPLETED:
                icon = "[OK]";
                iconColor = GREEN;
                break;
            case FAILED:
            case EXPIRED:
                icon = "[X]";
                iconColor = RED;
                break;
            case ABANDONED:
                icon = "[!]";
                iconColor = GOLD;
                break;
            default:
                icon = "[-]";
                iconColor = DIM;
        }

        Label iconLabel = new Label(icon, skin, "body");
        iconLabel.setColor(iconColor);
        row.add(iconLabel).padRight(8);

        Label nameLabel = new Label(record.questName, skin, "body");
        if (record.outcome == QuestOutcome.FAILED || record.outcome == QuestOutcome.EXPIRED) {
            nameLabel.setColor(Color.GRAY);
        } else {
            nameLabel.setColor(Color.LIGHT_GRAY);
        }
        row.add(nameLabel).expandX().left();

        Label timeLabel = new Label(formatTimeAgo(record.timestampMs), skin, "slot-detail");
        row.add(timeLabel).right().padLeft(8);

        if (record.creditsEarned > 0) {
            Label credLabel = new Label("+" + record.creditsEarned + " Cr", skin, "slot-detail");
            credLabel.setColor(GOLD);
            row.add(credLabel).right().padLeft(8);
        }
        if (record.reputationDelta != 0) {
            String sign = record.reputationDelta > 0 ? "+" : "";
            Label repLabel = new Label(sign + (int) record.reputationDelta + " Rep", skin, "slot-detail");
            repLabel.setColor(record.reputationDelta > 0 ? GREEN : RED);
            row.add(repLabel).right().padLeft(8);
        }

        TextButton selectBtn = new TextButton(">", skin, "small");
        selectBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDetail(record);
            }
        });
        row.add(selectBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(CompletedQuestRecord record) {
        detailTable.clear();

        Label nameLabel = new Label(record.questName, skin, "header");
        nameLabel.setColor(getOutcomeColor(record.outcome));
        detailTable.add(nameLabel).left().padBottom(4).row();

        Label typeLabel = new Label(record.questType, skin, "slot-detail");
        detailTable.add(typeLabel).left().padBottom(8).row();

        Label outcomeHeader = new Label("OUTCOME", skin, "slot-name");
        outcomeHeader.setColor(CYAN);
        detailTable.add(outcomeHeader).left().padBottom(4).row();

        Label outcomeLabel = new Label(record.outcome.name(), skin, "body");
        outcomeLabel.setColor(getOutcomeColor(record.outcome));
        detailTable.add(outcomeLabel).left().padBottom(8).row();

        if (record.creditsEarned > 0 || record.reputationDelta != 0) {
            String rewardTitle = record.outcome == QuestOutcome.COMPLETED ? "REWARDS EARNED" : "PENALTIES";
            Label rewardHeader = new Label(rewardTitle, skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padBottom(4).row();

            if (record.creditsEarned > 0) {
                Label credLabel = new Label(record.creditsEarned + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (record.reputationFaction != null && record.reputationDelta != 0) {
                String sign = record.reputationDelta > 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) record.reputationDelta + " " + record.reputationFaction, skin, "body");
                repLabel.setColor(record.reputationDelta > 0 ? GREEN : RED);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        Label timeHeader = new Label("COMPLETED", skin, "slot-name");
        timeHeader.setColor(CYAN);
        detailTable.add(timeHeader).left().padTop(8).padBottom(4).row();
        Label timeLabel = new Label(formatTimeAgo(record.timestampMs), skin, "body");
        detailTable.add(timeLabel).left().row();
    }

    private Color getOutcomeColor(QuestOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return GREEN;
            case FAILED:
            case EXPIRED: return RED;
            case ABANDONED: return GOLD;
            default: return Color.WHITE;
        }
    }

    private String formatTimeAgo(long timestampMs) {
        long diff = System.currentTimeMillis() - timestampMs;
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        if (days > 0) return days + (days == 1 ? " day ago" : " days ago");
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        if (hours > 0) return hours + (hours == 1 ? " hour ago" : " hours ago");
        return "Just now";
    }
}
