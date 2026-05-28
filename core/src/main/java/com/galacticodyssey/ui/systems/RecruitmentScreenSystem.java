package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.npc.components.CantinaSeatComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.RecruitableComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.CandidateSelectedEvent;
import com.galacticodyssey.npc.events.CrewMemberHiredEvent;
import com.galacticodyssey.npc.events.RecruitmentClosedEvent;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;
import com.galacticodyssey.ui.actors.CantinaSceneActor;
import com.galacticodyssey.ui.actors.CandidateDetailOverlay;
import com.galacticodyssey.ui.actors.HireConfirmationDialog;
import com.galacticodyssey.ui.actors.HiringBoardOverlay;
import com.galacticodyssey.ui.actors.NpcPortraitActor;
import com.galacticodyssey.ui.actors.ResultToast;

import java.util.ArrayList;
import java.util.List;

public class RecruitmentScreenSystem implements Disposable {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
            ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
            ComponentMapper.getFor(RecruitableComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
            ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<CantinaSeatComponent> SEAT_M =
            ComponentMapper.getFor(CantinaSeatComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M =
            ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CrewMemberComponent> CREW_M =
            ComponentMapper.getFor(CrewMemberComponent.class);

    private final EventBus eventBus;
    private final Skin skin;
    private boolean open;

    private Stage stage;
    private Engine engine;
    private CantinaSceneActor cantinaScene;
    private Table portraitGroup;
    private CandidateDetailOverlay detailOverlay;
    private HiringBoardOverlay hiringBoard;
    private HireConfirmationDialog confirmDialog;
    private ResultToast toast;
    private final List<NpcPortraitActor> portraitActors = new ArrayList<>();

    public RecruitmentScreenSystem(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;

        eventBus.subscribe(RecruitmentOpenedEvent.class, this::onOpened);
    }

    public void initialize(Engine engine) {
        this.engine = engine;

        stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        cantinaScene = new CantinaSceneActor(WORLD_WIDTH, WORLD_HEIGHT);
        stage.addActor(cantinaScene);

        portraitGroup = new Table();
        portraitGroup.setFillParent(true);
        portraitGroup.top().padTop(120);
        stage.addActor(portraitGroup);

        detailOverlay = new CandidateDetailOverlay(skin, this::onTalk, this::onDismissDetail);
        detailOverlay.setSize(WORLD_WIDTH, 200);
        detailOverlay.setPosition(0, 0);
        stage.addActor(detailOverlay);

        hiringBoard = new HiringBoardOverlay(skin, this::onCandidateFromBoard, this::closeHiringBoard);
        hiringBoard.setFillParent(true);
        stage.addActor(hiringBoard);

        confirmDialog = new HireConfirmationDialog(skin, this::onConfirmHire, this::onDeclineHire);
        confirmDialog.setSize(500, 400);
        confirmDialog.setPosition((WORLD_WIDTH - 500) / 2, (WORLD_HEIGHT - 400) / 2);
        stage.addActor(confirmDialog);

        toast = new ResultToast(skin);
        toast.setFillParent(true);
        stage.addActor(toast);
    }

    public boolean isOpen() { return open; }

    public Stage getStage() { return stage; }

    public void close() {
        if (!open) return;
        open = false;
        detailOverlay.hide();
        hiringBoard.hide();
        confirmDialog.hide();
        clearPortraits();
        eventBus.publish(new RecruitmentClosedEvent());
    }

    public void render(float delta) {
        if (!open || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        clearPortraits();
        if (cantinaScene != null) cantinaScene.dispose();
        if (detailOverlay != null) detailOverlay.dispose();
        if (hiringBoard != null) hiringBoard.dispose();
        if (confirmDialog != null) confirmDialog.dispose();
        if (stage != null) stage.dispose();
    }

    private void onOpened(RecruitmentOpenedEvent event) {
        open = true;
        populatePortraits();
    }

    private void populatePortraits() {
        clearPortraits();
        if (engine == null) return;

        ImmutableArray<Entity> candidates = engine.getEntitiesFor(
                Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        for (int i = 0; i < candidates.size(); i++) {
            Entity npc = candidates.get(i);
            CantinaSeatComponent seat = SEAT_M.get(npc);

            NpcPortraitActor portrait = new NpcPortraitActor(npc, skin, this::onPortraitClicked);
            portrait.setPosition(seat.sceneX, seat.sceneY);
            portraitActors.add(portrait);
            portraitGroup.addActor(portrait);
        }
    }

    private void clearPortraits() {
        for (NpcPortraitActor actor : portraitActors) {
            actor.remove();
            actor.dispose();
        }
        portraitActors.clear();
    }

    private void onPortraitClicked(Entity npc) {
        for (NpcPortraitActor actor : portraitActors) {
            actor.setSelected(actor.getEntity() == npc);
        }
        detailOverlay.showCandidate(npc);
        eventBus.publish(new CandidateSelectedEvent(npc));
    }

    private void onCandidateFromBoard(Entity npc) {
        hiringBoard.hide();
        onPortraitClicked(npc);
    }

    private void onTalk(Entity npc) {
        confirmDialog.show(npc, getCrewCount(), getCrewMax(), getPlayerCredits());
    }

    private void onDismissDetail() {
        detailOverlay.hide();
        for (NpcPortraitActor actor : portraitActors) {
            actor.setSelected(false);
        }
    }

    private void closeHiringBoard() {
        hiringBoard.hide();
    }

    private void onConfirmHire(Entity npc) {
        long signingCost = getSigningCost(npc);
        deductCredits(signingCost);

        executeHire(npc, eventBus);

        confirmDialog.hide();
        detailOverlay.hide();

        for (int i = portraitActors.size() - 1; i >= 0; i--) {
            if (portraitActors.get(i).getEntity() == npc) {
                NpcPortraitActor actor = portraitActors.remove(i);
                actor.remove();
                actor.dispose();
                break;
            }
        }

        NpcIdentityComponent identity = IDENTITY_M.get(npc);
        String name = identity != null && identity.name != null ? identity.name : "recruit";
        toast.show(name + " hired!", null);
    }

    private void onDeclineHire() {
        confirmDialog.hide();
    }

    private int getCrewCount() {
        if (engine == null) return 0;
        return engine.getEntitiesFor(Family.all(CrewMemberComponent.class).get()).size();
    }

    private int getCrewMax() {
        return 12;
    }

    private long getPlayerCredits() {
        if (engine == null) return 0;
        ImmutableArray<Entity> players = engine.getEntitiesFor(
                Family.all(PlayerTagComponent.class, PlayerWalletComponent.class).get());
        if (players.size() == 0) return 0;
        return WALLET_M.get(players.first()).credits;
    }

    private long getSigningCost(Entity npc) {
        RecruitableComponent rc = RECRUIT_M.get(npc);
        if (rc == null) return 0;
        float wage = rc.negotiatedWage > 0 ? rc.negotiatedWage : rc.askingWageMax;
        return (long) (wage * 2);
    }

    private void deductCredits(long amount) {
        if (engine == null) return;
        ImmutableArray<Entity> players = engine.getEntitiesFor(
                Family.all(PlayerTagComponent.class, PlayerWalletComponent.class).get());
        if (players.size() == 0) return;
        PlayerWalletComponent wallet = WALLET_M.get(players.first());
        wallet.credits -= amount;
    }

    public static void executeHire(Entity npc, EventBus eventBus) {
        RecruitableComponent rc = ComponentMapper.getFor(RecruitableComponent.class).get(npc);
        NpcIdentityComponent identity = ComponentMapper.getFor(NpcIdentityComponent.class).get(npc);

        float wage = (rc != null && rc.negotiatedWage > 0f) ? rc.negotiatedWage : (rc != null ? rc.askingWageMax : 0f);

        CrewRole crewRole = null;
        if (identity != null && identity.role != null) {
            crewRole = identity.role.toCrewRole();
        }

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.wage = wage;
        crew.morale = 75f;
        crew.loyalty = 50f;
        crew.role = crewRole;
        npc.add(crew);

        npc.remove(RecruitableComponent.class);
        npc.remove(CantinaSeatComponent.class);

        if (eventBus != null) {
            eventBus.publish(new CrewMemberHiredEvent(npc, crewRole));
        }
    }
}
