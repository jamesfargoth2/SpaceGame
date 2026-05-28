package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

public class BuildQueuePanel extends Table {
    private final BuildOrder buildOrder;
    private final Skin skin;
    private final Label totalCostLabel;
    private final Table actionList;

    public BuildQueuePanel(BuildOrder buildOrder, int playerCredits, Skin skin) {
        super(skin);
        this.buildOrder = buildOrder;
        this.skin = skin;

        add(new Label("BUILD ORDER QUEUE", skin)).colspan(2).center().padBottom(8).row();

        actionList = new Table(skin);
        ScrollPane scroll = new ScrollPane(actionList, skin);
        add(scroll).expand().fill().colspan(2).row();

        add(new Label("Total Cost:", skin)).left().padTop(8);
        totalCostLabel = new Label("0 cr", skin);
        add(totalCostLabel).right().padTop(8).row();

        add(new Label("Credits:", skin)).left();
        add(new Label(playerCredits + " cr", skin)).right().row();

        add(new Label("Apply at next Shipyard dock", skin)).colspan(2).center().padTop(12).row();

        refresh();
    }

    public void refresh() {
        actionList.clear();
        for (int i = 0; i < buildOrder.actions.size(); i++) {
            BuildAction action = buildOrder.actions.get(i);
            Table row = new Table(skin);
            row.add(new Label(action.description, skin)).expandX().left();
            row.add(new Label(action.cost + " cr", skin)).right();

            int idx = i;
            TextButton removeBtn = new TextButton("X", skin);
            removeBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    buildOrder.removeAction(idx);
                    refresh();
                }
            });
            row.add(removeBtn).padLeft(8);
            actionList.add(row).fillX().padBottom(4).row();
        }
        totalCostLabel.setText(buildOrder.totalCost() + " cr");
    }
}
