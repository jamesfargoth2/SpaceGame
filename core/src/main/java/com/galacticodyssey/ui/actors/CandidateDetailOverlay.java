package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewRank;

import java.util.EnumSet;
import java.util.function.Consumer;

public class CandidateDetailOverlay extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onTalk;
    private final Runnable onDismiss;
    private Texture bgTexture;
    private Entity currentEntity;

    public CandidateDetailOverlay(Skin skin, Consumer<Entity> onTalk, Runnable onDismiss) {
        this.skin = skin;
        this.onTalk = onTalk;
        this.onDismiss = onDismiss;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.04f, 0.05f, 0.09f, 0.92f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(16);
        setVisible(false);
    }

    public void showCandidate(Entity entity) {
        this.currentEntity = entity;
        clearChildren();
        setVisible(true);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        // Top row: name + info + wage + buttons
        Table topRow = new Table();

        // Name block
        Table nameBlock = new Table();
        String name = identity.name != null ? identity.name : "Unknown";
        Label nameLabel = new Label(name, skin, "header");
        nameBlock.add(nameLabel).left().row();

        String info = (identity.species != null ? identity.species : "Unknown") +
            " · " + (identity.role != null ? identity.role.name() : "Unknown") +
            " · " + CrewRank.RECRUIT.name();
        Label infoLabel = new Label(info, skin, "slot-detail");
        nameBlock.add(infoLabel).left().row();

        // Quote
        if (rc != null && rc.hookLine != null) {
            Label quoteLabel = new Label("\"" + rc.hookLine + "\"", skin, "slot-meta");
            quoteLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            quoteLabel.setFontScale(0.9f);
            nameBlock.add(quoteLabel).left().padTop(4).row();
        }

        topRow.add(nameBlock).expandX().left();

        // Wage
        if (rc != null) {
            String wage = (int) rc.askingWageMin + "–" + (int) rc.askingWageMax + " cr/wk";
            Label wageLabel = new Label(wage, skin, "slot-name");
            wageLabel.setColor(1f, 0.84f, 0f, 1f);
            topRow.add(wageLabel).right().padRight(16);
        }

        // Buttons
        Table buttons = new Table();
        TextButton talkBtn = new TextButton("Talk", skin, "default");
        talkBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (currentEntity != null) onTalk.accept(currentEntity);
            }
        });
        buttons.add(talkBtn).width(120).height(36).padBottom(4).row();

        TextButton dismissBtn = new TextButton("Dismiss", skin, "default");
        dismissBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                onDismiss.run();
            }
        });
        buttons.add(dismissBtn).width(120).height(36);

        topRow.add(buttons).right();

        add(topRow).expandX().fillX().row();

        // Stat bars
        if (stats != null && rc != null) {
            Table statsTable = new Table();
            statsTable.padTop(8);
            EnumSet<StatType> revealed = rc.revealedStats;
            int col = 0;
            for (StatType st : StatType.values()) {
                String text;
                Color color;
                if (revealed.contains(st)) {
                    text = st.abbreviation + " " + (int) st.getValue(stats);
                    color = new Color(0.33f, 0.81f, 0.55f, 1f);
                } else {
                    text = st.abbreviation + " ???";
                    color = new Color(0.33f, 0.33f, 0.33f, 1f);
                }
                Label statLabel = new Label(text, skin, "slot-meta");
                statLabel.setColor(color);
                statsTable.add(statLabel).left().padRight(20).minWidth(80);
                col++;
                if (col % 4 == 0) statsTable.row();
            }
            add(statsTable).expandX().left();
        }

        // Slide-up animation
        float targetY = getY();
        setY(targetY - getHeight());
        addAction(Actions.moveTo(getX(), targetY, 0.2f, Interpolation.circleOut));
    }

    public void hide() {
        addAction(Actions.sequence(
            Actions.moveBy(0, -getHeight(), 0.15f, Interpolation.circleIn),
            Actions.run(() -> {
                setVisible(false);
                currentEntity = null;
            })
        ));
    }

    public void refreshStats(Entity entity) {
        if (currentEntity == entity) {
            showCandidate(entity);
        }
    }

    public Entity getCurrentEntity() {
        return currentEntity;
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
