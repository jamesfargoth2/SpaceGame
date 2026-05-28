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

import java.util.function.Consumer;

public class HireConfirmationDialog extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onHire;
    private final Runnable onDecline;
    private Texture bgTexture;
    private Entity currentEntity;

    public HireConfirmationDialog(Skin skin, Consumer<Entity> onHire, Runnable onDecline) {
        this.skin = skin;
        this.onHire = onHire;
        this.onDecline = onDecline;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.06f, 0.08f, 0.14f, 0.96f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(20);
        setVisible(false);
    }

    public void show(Entity entity, int crewSlotsFilled, int crewSlotsMax, long playerCredits) {
        this.currentEntity = entity;
        clearChildren();
        setVisible(true);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        // Title
        String name = identity.name != null ? identity.name : "Unknown";
        Label title = new Label("HIRE " + name.toUpperCase() + "?", skin, "header");
        title.setColor(1f, 0.84f, 0f, 1f);
        add(title).left().padBottom(12).row();

        // Full stat sheet (all revealed at offer time)
        if (stats != null) {
            Table statsTable = new Table();
            for (StatType st : StatType.values()) {
                Label statLabel = new Label(
                    st.abbreviation + " " + (int) st.getValue(stats), skin, "slot-detail");
                statLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
                statsTable.add(statLabel).left().padRight(16).minWidth(80);
            }
            add(statsTable).left().padBottom(8).row();
        }

        // Wage
        if (rc != null) {
            float wage = rc.negotiatedWage > 0 ? rc.negotiatedWage : rc.askingWageMax;
            Label wageLabel = new Label("Wage: " + (int) wage + " cr/week", skin, "slot-name");
            wageLabel.setColor(1f, 0.84f, 0f, 1f);
            add(wageLabel).left().padBottom(4).row();

            long signingBonus = (long) (wage * 2);
            Label bonusLabel = new Label("Signing bonus: " + signingBonus + " cr", skin, "slot-detail");
            add(bonusLabel).left().padBottom(8).row();

            // Conditions
            if (!rc.conditions.isEmpty()) {
                Label condHeader = new Label("Conditions:", skin, "slot-detail");
                add(condHeader).left().padBottom(4).row();
                for (RecruitCondition cond : rc.conditions) {
                    Label condLabel = new Label("• " + cond.description, skin, "slot-meta");
                    condLabel.setColor(cond.met ? new Color(0.33f, 0.81f, 0.55f, 1f) :
                                                  new Color(0.9f, 0.27f, 0.37f, 1f));
                    add(condLabel).left().row();
                }
            }

            // Crew slots
            Label slotLabel = new Label(
                "Crew: " + (crewSlotsFilled + 1) + "/" + crewSlotsMax + " slots",
                skin, "slot-detail");
            add(slotLabel).left().padTop(8).padBottom(12).row();

            // Buttons
            Table buttons = new Table();
            boolean canAfford = playerCredits >= signingBonus;

            TextButton hireBtn = new TextButton("Hire", skin, "default");
            hireBtn.setDisabled(!canAfford);
            if (!canAfford) {
                hireBtn.setColor(0.4f, 0.4f, 0.4f, 1f);
            }
            hireBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (canAfford && currentEntity != null) {
                        onHire.accept(currentEntity);
                    }
                }
            });
            buttons.add(hireBtn).width(140).height(40).padRight(12);

            TextButton declineBtn = new TextButton("Decline", skin, "default");
            declineBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onDecline.run();
                }
            });
            buttons.add(declineBtn).width(140).height(40);

            add(buttons).center();
        }
    }

    public void hide() {
        setVisible(false);
        currentEntity = null;
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
