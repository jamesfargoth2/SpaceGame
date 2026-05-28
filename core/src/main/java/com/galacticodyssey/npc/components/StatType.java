package com.galacticodyssey.npc.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

public enum StatType {
    ACCURACY("ACC", s -> s.accuracy),
    REPAIR("REP", s -> s.repair),
    MEDICAL("MED", s -> s.medical),
    PILOTING("PIL", s -> s.piloting),
    SCIENCE("SCI", s -> s.science),
    COMBAT("CMB", s -> s.combat),
    PERSUASION("PER", s -> s.persuasion),
    STEALTH("STL", s -> s.stealth);

    public final String abbreviation;
    private final ToDoubleFunction<NpcStatsComponent> getter;

    StatType(String abbreviation, ToDoubleFunction<NpcStatsComponent> getter) {
        this.abbreviation = abbreviation;
        this.getter = getter;
    }

    public float getValue(NpcStatsComponent stats) {
        return (float) getter.applyAsDouble(stats);
    }

    public static List<StatType> getTopN(NpcStatsComponent stats, int n) {
        List<StatType> sorted = new ArrayList<>(List.of(values()));
        sorted.sort(Comparator.comparingDouble((StatType t) -> t.getValue(stats)).reversed());
        return sorted.subList(0, Math.min(n, sorted.size()));
    }
}
