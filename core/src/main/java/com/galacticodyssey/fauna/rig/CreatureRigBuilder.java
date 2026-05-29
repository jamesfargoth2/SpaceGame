package com.galacticodyssey.fauna.rig;

import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.part.Socket;

import java.util.HashMap;
import java.util.Map;

public final class CreatureRigBuilder {

    public CreatureRig build(CreatureSpec spec) {
        Map<AssembledNode, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < spec.allNodes.size(); i++) {
            indexMap.put(spec.allNodes.get(i), i);
        }

        Bone[] bones = new Bone[spec.allNodes.size()];
        for (int i = 0; i < spec.allNodes.size(); i++) {
            AssembledNode node = spec.allNodes.get(i);
            int parentIdx = findParentIndex(node, spec, indexMap);
            BoneRole role = resolveRole(node, parentIdx, spec);
            bones[i] = new Bone(i, parentIdx, role, "bone_" + i, node.localTransform);
        }
        return new CreatureRig(bones);
    }

    private int findParentIndex(AssembledNode node, CreatureSpec spec,
                                Map<AssembledNode, Integer> indexMap) {
        if (node == spec.root) return -1;
        for (AssembledNode candidate : spec.allNodes) {
            if (candidate.children.contains(node)) {
                return indexMap.get(candidate);
            }
        }
        return -1;
    }

    private BoneRole resolveRole(AssembledNode node, int parentIdx, CreatureSpec spec) {
        if (parentIdx < 0) return BoneRole.STRUCTURAL; // root torso
        AssembledNode parent = spec.allNodes.get(parentIdx);
        for (Socket s : parent.part.sockets) {
            if (s.acceptedType == node.part.partType) {
                return BoneRole.fromJointHint(s.jointHint);
            }
        }
        return BoneRole.STRUCTURAL;
    }
}
