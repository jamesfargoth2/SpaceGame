package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RecruitableComponent implements Component {
    public float askingWageMin;
    public float askingWageMax;
    public float negotiatedWage = -1f;
    public final List<RecruitCondition> conditions = new ArrayList<>();
    public final EnumSet<StatType> revealedStats = EnumSet.noneOf(StatType.class);
    public String dialogTreeId;
    public RecruitInteractionState interactionState = RecruitInteractionState.UNMET;
    public String hookLine;
}
