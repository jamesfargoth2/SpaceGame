package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.*;
import com.galacticodyssey.ui.actors.*;

import java.util.ArrayList;
import java.util.List;

public class RecruitmentScreenSystem implements Disposable {

    public enum ScreenState { CLOSED, BROWSE, SELECTED, DIALOG, OFFER, RESULT }

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);
    private static final ComponentMapper<CantinaSeatComponent> SEAT_M =
        ComponentMapper.getFor(CantinaSeatComponent.class);

    private final EventBus eventBus;
    private final Skin skin;
    private Stage stage;
    private Engine engine;

    private ScreenState state = ScreenState.CLOSED;
    private String currentStationId;
    private Entity selectedEntity;

    private CantinaSceneActor sceneActor;
    private CandidateDetailOverlay detailOverlay;
    private HiringBoardOverlay boardOverlay;
    private HireConfirmationDialog confirmDialog;
    private ResultToast resultToast;
    private Table headerBar;
    private final List<NpcPortraitActor> portraitActors = new ArrayList<>();

    public RecruitmentScreenSystem(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;

        eventBus.subscribe(RecruitmentOpenedEvent.class, this::onRecruitmentOpened);
        eventBus.subscribe(StatRevealedEvent.class, this::onStatRevealed);
        eventBus.subscribe(WageNegotiatedEvent.class, this::onWageNegotiated);
    }

    public void initialize(Engine engine) {
        this.engine = engine;
        stage = new Stage(new FitViewport(1280, 720));

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    handleEscape();
                    return true;
                }
                return false;
            }
        });
    }

    private void onRecruitmentOpened(RecruitmentOpenedEvent event) {
        this.currentStationId = event.stationId;
        open();
    }

    public void open() {
        if (state != ScreenState.CLOSED || stage == null) return;

        state = ScreenState.BROWSE;
        buildScene();
    }

    public void close() {
        if (state == ScreenState.CLOSED) return;

        state = ScreenState.CLOSED;
        selectedEntity = null;
        currentStationId = null;
        clearScene();
        eventBus.publish(new RecruitmentClosedEvent());
    }

    private void buildScene() {
        stage.clear();
        portraitActors.clear();

        float w = stage.getViewport().getWorldWidth();
        float h = stage.getViewport().getWorldHeight();

        // Background
        sceneActor = new CantinaSceneActor(w, h);
        stage.addActor(sceneActor);

        // Hiring Board (clickable scene element)
        TextButton boardButton = new TextButton("HIRING\nBOARD", skin, "default");
        boardButton.setSize(100, 60);
        boardButton.setPosition(w * 0.30f, h * (1f - 0.22f));
        boardButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openHiringBoard();
            }
        });
        stage.addActor(boardButton);

        // NPC portraits
        ImmutableArray<Entity> candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        for (Entity entity : candidates) {
            CantinaSeatComponent seat = SEAT_M.get(entity);
            NpcPortraitActor portrait = new NpcPortraitActor(entity, skin, this::onNpcClicked);
            portrait.setPosition(seat.sceneX * w, (1f - seat.sceneY) * h);
            stage.addActor(portrait);
            portraitActors.add(portrait);
        }

        // Detail overlay
        detailOverlay = new CandidateDetailOverlay(skin, this::onTalkClicked, this::onDismissClicked);
        detailOverlay.setSize(w, h * 0.25f);
        detailOverlay.setPosition(0, 0);
        stage.addActor(detailOverlay);

        // Hiring board overlay
        boardOverlay = new HiringBoardOverlay(skin, this::onBoardCandidateSelected, this::onBoardClosed);
        boardOverlay.setSize(w * 0.6f, h * 0.7f);
        boardOverlay.setPosition(w * 0.2f, h * 0.15f);
        stage.addActor(boardOverlay);

        // Confirmation dialog
        confirmDialog = new HireConfirmationDialog(skin, this::onHireConfirmed, this::onDeclined);
        confirmDialog.setSize(w * 0.4f, h * 0.6f);
        confirmDialog.setPosition(w * 0.3f, h * 0.2f);
        stage.addActor(confirmDialog);

        // Result toast
        resultToast = new ResultToast(skin);
        resultToast.setSize(w, 60);
        resultToast.setPosition(0, h * 0.7f);
        stage.addActor(resultToast);

        // Header bar
        headerBar = new Table();
        headerBar.setSize(w, 40);
        headerBar.setPosition(0, h - 40);
        headerBar.pad(8, 16, 8, 16);

        String stationName = currentStationId != null ? currentStationId.replace("_", " ") : "Unknown";
        Label locationLabel = new Label(stationName + " — Cantina", skin, "slot-name");
        locationLabel.setColor(0.9f, 0.27f, 0.37f, 1f);
        headerBar.add(locationLabel).expandX().left();

        Label candidateCount = new Label(candidates.size() + " candidates", skin, "slot-detail");
        headerBar.add(candidateCount).right();

        stage.addActor(headerBar);
    }

    private void clearScene() {
        for (NpcPortraitActor portrait : portraitActors) {
            portrait.dispose();
        }
        portraitActors.clear();
        if (sceneActor != null) {
            sceneActor.dispose();
            sceneActor = null;
        }
        if (detailOverlay != null) {
            detailOverlay.dispose();
            detailOverlay = null;
        }
        if (boardOverlay != null) {
            boardOverlay.dispose();
            boardOverlay = null;
        }
        if (confirmDialog != null) {
            confirmDialog.dispose();
            confirmDialog = null;
        }
        if (stage != null) {
            stage.clear();
        }
    }

    // -- State transitions --

    private void onNpcClicked(Entity entity) {
        if (state == ScreenState.BROWSE || state == ScreenState.SELECTED) {
            for (NpcPortraitActor p : portraitActors) {
                p.setSelected(p.getEntity() == entity);
            }
            selectedEntity = entity;
            state = ScreenState.SELECTED;
            detailOverlay.showCandidate(entity);
        }
    }

    private void onTalkClicked(Entity entity) {
        if (state != ScreenState.SELECTED) return;
        state = ScreenState.DIALOG;
        detailOverlay.hide();

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);
        if (identity != null && rc != null) {
            rc.interactionState = RecruitInteractionState.TALKED;
            eventBus.publish(new CandidateSelectedEvent(entity));
        }
    }

    public void onDialogComplete(Entity entity, boolean offerMade) {
        if (offerMade) {
            state = ScreenState.OFFER;
            RecruitableComponent rc = RECRUIT_M.get(entity);
            if (rc != null) {
                for (StatType st : StatType.values()) {
                    rc.revealedStats.add(st);
                }
                rc.interactionState = RecruitInteractionState.OFFERED;
            }
            confirmDialog.show(entity, getCrewCount(), getMaxCrewSlots(), getPlayerCredits());
        } else {
            state = ScreenState.BROWSE;
            selectedEntity = null;
            for (NpcPortraitActor p : portraitActors) {
                p.setSelected(false);
            }
        }
    }

    private void onHireConfirmed(Entity entity) {
        state = ScreenState.RESULT;
        confirmDialog.hide();

        executeHire(entity, eventBus);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        CrewMemberComponent crew = entity.getComponent(CrewMemberComponent.class);
        String name = identity != null && identity.name != null ? identity.name : "Unknown";
        String role = crew != null && crew.role != null ? crew.role.name() : "";
        int wage = crew != null ? (int) crew.wage : 0;

        // Remove portrait from scene
        portraitActors.removeIf(p -> {
            if (p.getEntity() == entity) {
                p.dispose();
                p.remove();
                return true;
            }
            return false;
        });

        resultToast.show(name + " hired — " + role + ", " + wage + " cr/wk", () -> {
            state = ScreenState.BROWSE;
            selectedEntity = null;
        });
    }

    private void onDeclined() {
        state = ScreenState.BROWSE;
        confirmDialog.hide();
        selectedEntity = null;
        for (NpcPortraitActor p : portraitActors) {
            p.setSelected(false);
        }
    }

    private void onDismissClicked() {
        state = ScreenState.BROWSE;
        detailOverlay.hide();
        selectedEntity = null;
        for (NpcPortraitActor p : portraitActors) {
            p.setSelected(false);
        }
    }

    public void openHiringBoard() {
        if (state != ScreenState.BROWSE) return;
        List<Entity> candidates = new ArrayList<>();
        for (NpcPortraitActor p : portraitActors) {
            candidates.add(p.getEntity());
        }
        String stationName = currentStationId != null ? currentStationId.replace("_", " ") : "Station";
        boardOverlay.show(stationName, candidates);
    }

    private void onBoardCandidateSelected(Entity entity) {
        boardOverlay.hide();
        onNpcClicked(entity);
    }

    private void onBoardClosed() {
        boardOverlay.hide();
    }

    private void handleEscape() {
        switch (state) {
            case SELECTED -> onDismissClicked();
            case OFFER -> onDeclined();
            case BROWSE -> {
                if (boardOverlay != null && boardOverlay.isVisible()) {
                    onBoardClosed();
                } else {
                    close();
                }
            }
            default -> {}
        }
    }

    private void onStatRevealed(StatRevealedEvent event) {
        if (detailOverlay != null) {
            detailOverlay.refreshStats(event.npcEntity);
        }
    }

    private void onWageNegotiated(WageNegotiatedEvent event) {
        RecruitableComponent rc = RECRUIT_M.get(event.npcEntity);
        if (rc != null) {
            rc.negotiatedWage = event.finalWage;
        }
    }

    // -- Hire logic (static for testability) --

    public static void executeHire(Entity npc, EventBus eventBus) {
        NpcIdentityComponent identity = ComponentMapper.getFor(NpcIdentityComponent.class).get(npc);
        RecruitableComponent rc = ComponentMapper.getFor(RecruitableComponent.class).get(npc);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = mapRole(identity != null ? identity.role : null);
        crew.rank = CrewRank.RECRUIT;
        crew.morale = 75f;
        crew.loyalty = 50f;
        crew.wage = rc != null && rc.negotiatedWage > 0 ? rc.negotiatedWage : (rc != null ? rc.askingWageMax : 0);
        npc.add(crew);

        npc.remove(RecruitableComponent.class);
        npc.remove(CantinaSeatComponent.class);

        eventBus.publish(new CrewMemberHiredEvent(npc, crew.role));
    }

    private static CrewRole mapRole(NPCRole npcRole) {
        if (npcRole == null) return CrewRole.MARINE;
        return switch (npcRole) {
            case PILOT -> CrewRole.PILOT;
            case GUNNER -> CrewRole.GUNNER;
            case ENGINEER -> CrewRole.ENGINEER;
            case MEDIC -> CrewRole.MEDIC;
            case MARINE -> CrewRole.MARINE;
            case SCIENTIST -> CrewRole.SCIENTIST;
            case NAVIGATOR -> CrewRole.NAVIGATOR;
            default -> CrewRole.MARINE;
        };
    }

    // -- Queries --

    private int getCrewCount() {
        if (engine == null) return 0;
        return engine.getEntitiesFor(Family.all(CrewMemberComponent.class).get()).size();
    }

    private int getMaxCrewSlots() {
        return 6;
    }

    private long getPlayerCredits() {
        if (engine == null) return 0;
        var players = engine.getEntitiesFor(Family.all(
            com.galacticodyssey.economy.components.PlayerWalletComponent.class).get());
        if (players.size() == 0) return 0;
        return players.first().getComponent(
            com.galacticodyssey.economy.components.PlayerWalletComponent.class).credits;
    }

    // -- Render & lifecycle --

    public void render(float delta) {
        if (state == ScreenState.CLOSED || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    public boolean isOpen() {
        return state != ScreenState.CLOSED;
    }

    public ScreenState getState() {
        return state;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public void dispose() {
        clearScene();
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
    }
}
