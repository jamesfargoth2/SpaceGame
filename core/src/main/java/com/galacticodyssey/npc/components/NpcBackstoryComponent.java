package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.BackstoryHook;

import java.util.ArrayList;
import java.util.List;

public class NpcBackstoryComponent implements Component {
    public final List<BackstoryHook> hooks = new ArrayList<>();
}
