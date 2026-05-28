package com.galacticodyssey.npc.data;

import java.util.ArrayList;
import java.util.List;

public class DialogNode {
    public String id;
    public String speakerLabel;
    public String text;
    public List<DialogChoice> choices = new ArrayList<>();

    public boolean isEndNode() {
        return choices == null || choices.isEmpty();
    }
}
