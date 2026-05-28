package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.shipbuilder.*;

import java.util.List;
import java.util.function.Consumer;

public class ModuleBrowserPanel extends Window {
    public ModuleBrowserPanel(String hardpointId,
                              HardpointMarkerRenderer.HardpointDef hardpoint,
                              ShipStatsCalculator statsCalc,
                              BlueprintRegistry blueprints,
                              ShipDesign design,
                              Skin skin,
                              Consumer<ModuleAssignment> onInstall) {
        super("Module Browser — " + hardpointId, skin);

        List<ModuleCatalogEntry> modules = statsCalc.getModulesByType(hardpoint.type);
        ModuleAssignment current = design.modules.get(hardpointId);

        Table list = new Table(skin);
        for (ModuleCatalogEntry module : modules) {
            boolean unlocked = blueprints.isUnlocked(findBlueprintId(blueprints, module.moduleId));
            Table row = new Table(skin);
            Label nameLabel = new Label(module.name, skin);
            if (!unlocked) nameLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            row.add(nameLabel).left().expandX();
            row.add(new Label(String.format("%.0f DPS", module.dps), skin)).padLeft(10);
            row.add(new Label(String.format("%.0f kW", module.powerDraw), skin)).padLeft(10);
            row.add(new Label(module.price + " cr", skin)).padLeft(10);

            boolean isCurrent = current != null && current.moduleId.equals(module.moduleId);
            if (isCurrent) {
                row.add(new Label("[INSTALLED]", skin)).padLeft(10);
            } else if (unlocked) {
                TextButton installBtn = new TextButton("Install", skin);
                installBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        onInstall.accept(new ModuleAssignment(module.moduleId,
                            findBlueprintId(blueprints, module.moduleId)));
                        remove();
                    }
                });
                row.add(installBtn).padLeft(10);
            } else {
                row.add(new Label("[LOCKED]", skin)).padLeft(10);
            }

            list.add(row).fillX().padBottom(4).row();
        }

        ScrollPane scroll = new ScrollPane(list, skin);
        add(scroll).expand().fill().width(500).height(300);
        pack();
        setModal(true);
        setMovable(true);
    }

    private String findBlueprintId(BlueprintRegistry registry, String moduleId) {
        for (BlueprintData bp : registry.getByType(BlueprintData.BlueprintType.MODULE)) {
            if (bp.unlocks.equals(moduleId)) return bp.blueprintId;
        }
        return "";
    }
}
