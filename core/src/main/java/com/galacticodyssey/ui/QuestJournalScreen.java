package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.mission.events.ObjectiveUpdatedEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.events.QuestFailedEvent;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.ui.actors.*;
import com.galacticodyssey.ui.events.JournalOpenedEvent;
import com.galacticodyssey.ui.events.JournalClosedEvent;

public class QuestJournalScreen implements Screen {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color TAB_INACTIVE = new Color(0.5f, 0.6f, 0.7f, 1f);
    private static final Color BG_COLOR = new Color(0.05f, 0.07f, 0.12f, 1f);

    private final GalacticOdyssey game;
    private final Screen returnTo;
    private final EventBus eventBus;
    private final QuestJournal journal;
    private final JobBoard jobBoard;
    private final JobRegistry jobRegistry;
    private final SagaRegistry sagaRegistry;
    private final ReputationQuery reputation;
    private final Skin skin;
    private final Stage stage;

    private final StoryTabActor storyTab;
    private final ActiveQuestsTabActor activeTab;
    private final JobBoardTabActor boardTab;
    private final RumoursTabActor rumoursTab;
    private final HistoryTabActor historyTab;

    private final Table contentArea;
    private final TextButton[] tabButtons;
    private int activeTabIndex = 0;
    private final Table[] tabContents;
    private boolean inputListenerAdded;

    private final EventBus.EventListener<ObjectiveUpdatedEvent> onObjectiveUpdated;
    private final EventBus.EventListener<QuestCompletedEvent> onQuestCompleted;
    private final EventBus.EventListener<QuestFailedEvent> onQuestFailed;
    private final EventBus.EventListener<QuestDiscoveredEvent> onQuestDiscovered;

    public QuestJournalScreen(GalacticOdyssey game, Screen returnTo,
                              EventBus eventBus, QuestJournal journal,
                              JobBoard jobBoard, JobRegistry jobRegistry,
                              SagaRegistry sagaRegistry, ReputationQuery reputation,
                              Skin skin) {
        this.game = game;
        this.returnTo = returnTo;
        this.eventBus = eventBus;
        this.journal = journal;
        this.jobBoard = jobBoard;
        this.jobRegistry = jobRegistry;
        this.sagaRegistry = sagaRegistry;
        this.reputation = reputation;
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());

        storyTab = new StoryTabActor(skin, journal, sagaRegistry, eventBus);
        activeTab = new ActiveQuestsTabActor(skin, journal, eventBus);
        boardTab = new JobBoardTabActor(skin, jobBoard, jobRegistry, journal, eventBus, reputation);
        rumoursTab = new RumoursTabActor(skin, journal);
        historyTab = new HistoryTabActor(skin, journal);

        tabContents = new Table[]{storyTab, activeTab, boardTab, rumoursTab, historyTab};

        Table root = new Table();
        root.setFillParent(true);
        root.pad(24);
        stage.addActor(root);

        // Title bar
        Table titleBar = new Table();
        Label title = new Label("QUEST JOURNAL", skin, "title");
        titleBar.add(title).expandX().left();
        TextButton closeBtn = new TextButton("X", skin, "small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        titleBar.add(closeBtn).right();
        root.add(titleBar).expandX().fillX().padBottom(12).row();

        // Tab bar
        Table tabBar = new Table();
        String[] tabNames = {"Story", "Active", "Board", "Rumours", "History"};
        tabButtons = new TextButton[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            tabButtons[i] = new TextButton(tabNames[i], skin);
            final int index = i;
            tabButtons[i].addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    selectTab(index);
                }
            });
            tabBar.add(tabButtons[i]).padRight(4).minWidth(100);
        }
        root.add(tabBar).left().padBottom(8).row();

        // Content area
        contentArea = new Table();
        root.add(contentArea).expand().fill();

        // Subscribe to events for live updates
        onObjectiveUpdated = e -> { storyTab.refresh(); activeTab.refresh(); };
        onQuestCompleted = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestFailed = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestDiscovered = e -> rumoursTab.refresh();

        eventBus.subscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
        eventBus.subscribe(QuestCompletedEvent.class, onQuestCompleted);
        eventBus.subscribe(QuestFailedEvent.class, onQuestFailed);
        eventBus.subscribe(QuestDiscoveredEvent.class, onQuestDiscovered);

        selectTab(0);
    }

    private void selectTab(int index) {
        activeTabIndex = index;
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].getLabel().setColor(i == index ? CYAN : TAB_INACTIVE);
        }
        contentArea.clear();
        contentArea.add(tabContents[index]).expand().fill();
        refreshActiveTab();
    }

    private void refreshActiveTab() {
        switch (activeTabIndex) {
            case 0: storyTab.refresh(); break;
            case 1: activeTab.refresh(); break;
            case 2: boardTab.refresh(); break;
            case 3: rumoursTab.refresh(); break;
            case 4: historyTab.refresh(); break;
        }
    }

    public void close() {
        eventBus.publish(new JournalClosedEvent());
        game.setScreen(returnTo);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        Gdx.input.setCursorCatched(false);
        eventBus.publish(new JournalOpenedEvent());

        if (!inputListenerAdded) {
            inputListenerAdded = true;
            stage.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.J) {
                        close();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        eventBus.unsubscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
        eventBus.unsubscribe(QuestCompletedEvent.class, onQuestCompleted);
        eventBus.unsubscribe(QuestFailedEvent.class, onQuestFailed);
        eventBus.unsubscribe(QuestDiscoveredEvent.class, onQuestDiscovered);
        stage.dispose();
    }
}
