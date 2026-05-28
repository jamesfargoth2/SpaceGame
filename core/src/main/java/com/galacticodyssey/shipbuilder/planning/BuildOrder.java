package com.galacticodyssey.shipbuilder.planning;

import com.galacticodyssey.shipbuilder.ShipDesign;

import java.util.ArrayList;
import java.util.List;

public class BuildOrder {
    public final List<BuildAction> actions = new ArrayList<>();

    public void addAction(BuildAction action) {
        actions.add(action);
    }

    public void removeAction(int index) {
        actions.remove(index);
    }

    public void reorder(int fromIndex, int toIndex) {
        BuildAction action = actions.remove(fromIndex);
        actions.add(toIndex, action);
    }

    public int totalCost() {
        int total = 0;
        for (BuildAction a : actions) total += a.cost;
        return total;
    }

    public void applyTo(ShipDesign design) {
        for (BuildAction action : actions) {
            switch (action.type) {
                case ADD_ROOM:
                    design.addRoom(action.roomDesign.copy());
                    break;
                case REMOVE_ROOM:
                    if (action.roomIndex >= 0 && action.roomIndex < design.rooms.size()) {
                        design.removeRoom(action.roomIndex);
                    }
                    break;
                case SWAP_MODULE:
                    design.setModule(action.hardpointId, action.moduleAssignment);
                    break;
                case HULL_TWEAK:
                    break;
            }
        }
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public void clear() {
        actions.clear();
    }
}
