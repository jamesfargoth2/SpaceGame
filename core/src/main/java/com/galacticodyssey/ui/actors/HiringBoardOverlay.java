package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewRole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HiringBoardOverlay extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onCandidateSelected;
    private final Runnable onClose;
    private final List<Entity> allCandidates = new ArrayList<>();
    private String activeFilter = null;
    private Table listContainer;
    private Texture bgTexture;

    public HiringBoardOverlay(Skin skin, Consumer<Entity> onCandidateSelected, Runnable onClose) {
        this.skin = skin;
        this.onCandidateSelected = onCandidateSelected;
        this.onClose = onClose;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.04f, 0.05f, 0.09f, 0.95f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(16);
        setVisible(false);
    }

    public void show(String stationName, List<Entity> candidates) {
        this.allCandidates.clear();
        this.allCandidates.addAll(candidates);
        this.activeFilter = null;
        setVisible(true);
        rebuild(stationName);
    }

    public void hide() {
        setVisible(false);
    }

    private void rebuild(String stationName) {
        clearChildren();

        // Header
        Table header = new Table();
        Label title = new Label("HIRING BOARD — " + stationName, skin, "header");
        title.setColor(0f, 0.9f, 1f, 1f);
        header.add(title).expandX().left();

        TextButton closeBtn = new TextButton("[ESC] Close", skin, "default");
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClose.run();
            }
        });
        header.add(closeBtn).right();
        add(header).expandX().fillX().padBottom(12).row();

        // Role filter tabs
        Table filterBar = new Table();
        addFilterTab(filterBar, "All", null);
        for (CrewRole role : CrewRole.values()) {
            addFilterTab(filterBar, role.name(), role.name());
        }
        add(filterBar).left().padBottom(12).row();

        // Candidate list
        listContainer = new Table();
        rebuildList();
        ScrollPane scrollPane = new ScrollPane(listContainer, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill();
    }

    private void addFilterTab(Table bar, String label, String filterValue) {
        long count = allCandidates.stream()
            .filter(e -> {
                if (filterValue == null) return true;
                NpcIdentityComponent id = IDENTITY_M.get(e);
                if (id.role == null) return false;
                com.galacticodyssey.npc.crew.CrewRole cr = id.role.toCrewRole();
                return cr != null && cr.name().equalsIgnoreCase(filterValue);
            }).count();

        TextButton tab = new TextButton(label + "(" + count + ")", skin, "default");
        tab.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                activeFilter = filterValue;
                rebuildList();
            }
        });
        bar.add(tab).padRight(4);
    }

    private void rebuildList() {
        listContainer.clearChildren();

        for (Entity entity : allCandidates) {
            NpcIdentityComponent identity = IDENTITY_M.get(entity);
            if (activeFilter != null) {
                if (identity.role == null) continue;
                com.galacticodyssey.npc.crew.CrewRole cr = identity.role.toCrewRole();
                if (cr == null || !cr.name().equalsIgnoreCase(activeFilter)) continue;
            }

            NpcStatsComponent stats = STATS_M.get(entity);
            RecruitableComponent rc = RECRUIT_M.get(entity);

            Table row = new Table();
            row.pad(8);

            String name = identity.name != null ? identity.name : "Unknown";
            Label nameLabel = new Label(name, skin, "slot-name");
            nameLabel.setColor(0.9f, 0.27f, 0.37f, 1f);
            row.add(nameLabel).left().minWidth(120);

            String info = (identity.species != null ? identity.species : "") +
                " · " + (identity.role != null ? identity.role.name() : "");
            Label infoLabel = new Label(info, skin, "slot-meta");
            infoLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            row.add(infoLabel).left().expandX().padLeft(8);

            if (stats != null) {
                List<StatType> top = StatType.getTopN(stats, 1);
                if (!top.isEmpty()) {
                    StatType best = top.get(0);
                    Label statLabel = new Label(
                        best.abbreviation + " " + (int) best.getValue(stats), skin, "slot-meta");
                    statLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
                    row.add(statLabel).right().padRight(16);
                }
            }

            if (rc != null) {
                Label wageLabel = new Label("~" + (int) ((rc.askingWageMin + rc.askingWageMax) / 2) + " cr",
                    skin, "slot-meta");
                wageLabel.setColor(1f, 0.84f, 0f, 1f);
                row.add(wageLabel).right();
            }

            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onCandidateSelected.accept(entity);
                }
            });

            listContainer.add(row).expandX().fillX().row();
        }
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
