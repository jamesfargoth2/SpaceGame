package com.galacticodyssey.fauna.animation;

public final class GaitControllerFactory {

    public static GaitController create(String gaitClass) {
        if (gaitClass == null) return new WalkGaitController();
        switch (gaitClass) {
            case "skitter": return new SkitterGaitController();
            case "slither": return new SlitherGaitController();
            case "walk":
            default:        return new WalkGaitController();
        }
    }

    private GaitControllerFactory() {}
}
