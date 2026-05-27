package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.NPCRole;

public class NpcIdentityComponent implements Component {
    public String npcId;
    public String name;
    public String species;
    public String background;
    public String portraitId;
    public NpcDisposition disposition = NpcDisposition.NEUTRAL;
    public String factionId;
    public boolean recruitable;
    public NPCRole role;
    public float age;
}