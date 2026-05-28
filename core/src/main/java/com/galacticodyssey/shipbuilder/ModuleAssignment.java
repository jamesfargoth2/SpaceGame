package com.galacticodyssey.shipbuilder;

public final class ModuleAssignment {
    public String moduleId;
    public String blueprintId;

    public ModuleAssignment() {}

    public ModuleAssignment(String moduleId, String blueprintId) {
        this.moduleId = moduleId;
        this.blueprintId = blueprintId;
    }
}
