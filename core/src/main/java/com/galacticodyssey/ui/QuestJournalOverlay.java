package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.events.ObjectiveUpdatedEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.events.QuestFailedEvent;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.ui.actors.ActiveQuestsTabActor;
import com.galacticodyssey.ui.actors.HistoryTabActor;
import com.galacticodyssey.ui.actors.JobBoardTabActor;
import com.galacticodyssey.ui.actors.RumoursTabActor;
import com.galacticodyssey.ui.actors.StoryTabActor;
import com.galacticodyssey.ui.events.JournalClosedEvent;
import com.galacticodyssey.ui.events.JournalOpenedEvent;

public class QuestJournalOverlay implements ManagedScreen {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color TAB_INACTIVE = new Color(0.5f, 0.6f, 0.7f, 1f);

    private final EventBus eventBus;
    private final Skin skin;

    private QuestJournal journal;
    private JobBoard jobBoard;
    private JobRegistry jobRegistry;
    private SagaRegistry sagaRegistry;
    private ReputationQuery reputation;

    private Stage stage;
    private Texture overlayTexture;
    private boolean open;
    private boolean initialized;

    private StoryTabActor storyTab;
    private ActiveQuestsTabActor activeTab;
    private JobBoardTabActor boardTab;
    private RumoursTabActor rumoursTab;
    private HistoryTabActor historyTab;

    private Table contentArea;
    private TextButton[] tabButtons;
    private int activeTabIndex;
    private Table[] tabContents;

    private EventBus.EventListener<ObjectiveUpdatedEvent> onObjectiveUpdated;
    private EventBus.EventListener<QuestCompletedEvent> onQuestCompleted;
    private EventBus.EventListener<QuestFailedEvent> onQuestFailed;
    private EventBus.EventListener<QuestDiscoveredEvent> onQuestDiscovered;

    public QuestJournalOverlay(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;
    }

    public void initialize(QuestJournal journal, JobBoard jobBoard,
                           JobRegistry jobRegistry, SagaRegistry sagaRegistry,
                           ReputationQuery reputation) {
        this.journal = journal;
        this.jobBoard = jobBoard;
        this.jobRegistry = jobRegistry;
        this.sagaRegistry = sagaRegistry;
        this.reputation = reputation;

        stage = new Stage(new ScreenViewport());

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0.05f, 0.07f, 0.12f, 0.95f));
        pix.fill();
        overlayTexture = new Texture(pix);
        pix.dispose();

        storyTab = new StoryTabActor(skin, journal, sagaRegistry, eventBus);
        activeTab = new ActiveQuestsTabActor(skin, journal, eventBus);
        boardTab = new JobBoardTabActor(skin, jobBoard, jobRegistry, journal, eventBus, reputation);
        rumoursTab = new RumoursTabActor(skin, journal);
        historyTab = new HistoryTabActor(skin, journal);

        tabContents = new Table[]{storyTab, activeTab, boardTab, rumoursTab, historyTab};

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        root.pad(24);

        Table titleBar = new Table();
        Label title = new Label("QUEST JOURNAL", skin, "title");
        titleBar.add(title).expandX().left();
        root.add(titleBar).expandX().fillX().padBottom(12).row();

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

        contentArea = new Table();
        root.add(contentArea).expand().fill();

        stage.addActor(root);

        onObjectiveUpdated = e -> { storyTab.refresh(); activeTab.refresh(); };
        onQuestCompleted = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestFailed = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestDiscovered = e -> rumoursTab.refresh();

        eventBus.subscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
        eventBus.subscribe(QuestCompletedEvent.class, onQuestCompleted);
        eventBus.subscribe(QuestFailedEvent.class, onQuestFailed);
        eventBus.subscribe(QuestDiscoveredEvent.class, onQuestDiscovered);

        selectTab(0);
        initialized = true;
    }

    public void setJobBoard(JobBoard jobBoard) {
        this.jobBoard = jobBoard;
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

    @Override
    public String getDisplayName() { return "Journal"; }

    @Override
    public void open() {
        if (open || !initialized) return;
        open = true;
        refreshActiveTab();
        eventBus.publish(new JournalOpenedEvent());
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        eventBus.publish(new JournalClosedEvent());
    }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public Stage getStage() { return stage; }

    @Override
    public void render(float delta) {
        if (!open || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (initialized) {
            eventBus.unsubscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
            eventBus.unsubscribe(QuestCompletedEvent.class, onQuestCompleted);
            eventBus.unsubscribe(QuestFailedEvent.class, onQuestFailed);
            eventBus.unsubscribe(QuestDiscoveredEvent.class, onQuestDiscovered);
        }
        if (stage != null) { stage.dispose(); stage = null; }
        if (overlayTexture != null) { overlayTexture.dispose(); overlayTexture = null; }
    }
}
