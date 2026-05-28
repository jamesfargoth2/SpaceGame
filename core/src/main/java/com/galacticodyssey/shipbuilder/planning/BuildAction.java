package com.galacticodyssey.shipbuilder.planning;

import com.galacticodyssey.shipbuilder.ModuleAssignment;
import com.galacticodyssey.shipbuilder.RoomDesign;

public class BuildAction {
    public enum ActionType { ADD_ROOM, REMOVE_ROOM, SWAP_MODULE, HULL_TWEAK }

    public ActionType type;
    public RoomDesign roomDesign;
    public int roomIndex;
    public String hardpointId;
    public ModuleAssignment moduleAssignment;
    public String description;
    public int cost;

    public BuildAction() {}

    public static BuildAction addRoom(RoomDesign room, int cost) {
        BuildAction a = new BuildAction();
        a.type = ActionType.ADD_ROOM;
        a.roomDesign = room;
        a.description = "+ Add " + room.type.name();
        a.cost = cost;
        return a;
    }

    public static BuildAction removeRoom(int roomIndex, String roomTypeName, int refund) {
        BuildAction a = new BuildAction();
        a.type = ActionType.REMOVE_ROOM;
        a.roomIndex = roomIndex;
        a.description = "- Remove " + roomTypeName;
        a.cost = -refund;
        return a;
    }

    public static BuildAction swapModule(String hardpointId, ModuleAssignment assignment,
                                          String moduleName, int cost) {
        BuildAction a = new BuildAction();
        a.type = ActionType.SWAP_MODULE;
        a.hardpointId = hardpointId;
        a.moduleAssignment = assignment;
        a.description = "⇄ " + hardpointId + " → " + moduleName;
        a.cost = cost;
        return a;
    }
}
