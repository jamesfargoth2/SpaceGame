package com.galacticodyssey.fauna.archetype;

import com.galacticodyssey.fauna.part.PartType;

import java.util.ArrayList;
import java.util.List;

/**
 * One node in an archetype's attachment tree. Places a part of {@link #partType} at the parent
 * part's socket {@link #socketId}. The root node has a null socketId (it IS the torso root).
 */
public final class AttachmentNode {
    /** Socket id on the PARENT part to attach to; null/empty only for the root node. */
    public String socketId = null;
    public PartType partType;
    /** If true, also place a mirrored copy of this part (and its subtree) across the YZ plane. */
    public boolean mirror = false;
    /**
     * Chain this many copies of the part end-to-end (>=1). Copy i+1 attaches to copy i's
     * {@link #continuationSocketId}. Used for serpentine spines. Children attach to the LAST copy.
     */
    public int repeat = 1;
    /** Socket id (on this part) used to chain repeats; required when repeat > 1. */
    public String continuationSocketId = null;
    public final List<AttachmentNode> children = new ArrayList<>();
}
