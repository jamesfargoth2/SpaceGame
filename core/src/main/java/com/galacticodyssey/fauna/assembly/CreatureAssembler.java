package com.galacticodyssey.fauna.assembly;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.archetype.AttachmentNode;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import com.galacticodyssey.fauna.part.Socket;

import java.util.List;
import java.util.Random;

/** Walks an archetype's attachment tree into a deterministic {@link CreatureSpec}. */
public final class CreatureAssembler {

    private final FaunaDataRegistry registry;

    public CreatureAssembler(FaunaDataRegistry registry) { this.registry = registry; }

    public CreatureSpec assemble(BodyPlanArchetypeDef arch, long seed) {
        Random rng = new Random(com.galacticodyssey.galaxy.SeedDeriver.faunaDomain(seed));

        CreatureSpec spec = new CreatureSpec();
        spec.seed = seed;
        spec.archetypeId = arch.id;
        spec.bodyPlan = arch.bodyPlan;
        spec.sizeMultiplier = arch.minSize + rng.nextFloat() * (arch.maxSize - arch.minSize);
        spec.colorSeed = rng.nextLong();

        // The root part may itself be a repeat chain (e.g. serpentine spine). Build the chain,
        // then attach the root template's children to the LAST segment. Chained segments must
        // expose the continuation socket, so restrict the candidate pool when repeat > 1.
        int rootRepeat = Math.max(1, arch.root.repeat);
        String rootCont = rootRepeat > 1 ? arch.root.continuationSocketId : null;
        CreaturePartDef rootPart = pick(arch.root.partType, arch, rng, rootCont);
        AssembledNode root = newNode(rootPart, new Matrix4(), spec.sizeMultiplier, false, spec);
        spec.root = root;

        AssembledNode last = root;
        CreaturePartDef lastPart = rootPart;
        for (int i = 1; i < rootRepeat; i++) {
            Socket cont = lastPart.findSocket(arch.root.continuationSocketId);
            if (cont == null) throw new IllegalStateException(
                "repeat chain on root of '" + arch.id + "' missing continuation socket '"
                + arch.root.continuationSocketId + "' on part '" + lastPart.id + "'");
            // Last segment needs no further continuation socket; intermediates do.
            String need = (i + 1 < rootRepeat) ? arch.root.continuationSocketId : null;
            CreaturePartDef next = pick(arch.root.partType, arch, rng, need);
            AssembledNode n = newNode(next, socketMatrix(cont, false, last.scale), last.scale, false, spec);
            last.children.add(n);
            n.worldTransform.set(last.worldTransform).mul(n.localTransform);
            last = n;
            lastPart = next;
        }

        attachChildren(arch.root, last, lastPart, arch, rng, spec);

        computeBounds(spec);
        return spec;
    }

    private void attachChildren(AttachmentNode parentTpl, AssembledNode parentNode,
                                CreaturePartDef parentPart, BodyPlanArchetypeDef arch,
                                Random rng, CreatureSpec spec) {
        for (AttachmentNode childTpl : parentTpl.children) {
            placeAttachment(childTpl, parentNode, parentPart, arch, rng, spec);
        }
    }

    private void placeAttachment(AttachmentNode tpl, AssembledNode parentNode,
                                 CreaturePartDef parentPart, BodyPlanArchetypeDef arch,
                                 Random rng, CreatureSpec spec) {
        Socket socket = parentPart.findSocket(tpl.socketId);
        if (socket == null) throw new IllegalStateException(
            "Archetype '" + arch.id + "' references missing socket '" + tpl.socketId
            + "' on part '" + parentPart.id + "'");

        int repeat = Math.max(1, tpl.repeat);
        CreaturePartDef chosen = pick(tpl.partType, arch, rng,
            repeat > 1 ? tpl.continuationSocketId : null);

        // Primary instance (+ repeat chain). Walk down the continuation socket for repeats.
        AssembledNode anchor = parentNode;
        Socket anchorSocket = socket;
        AssembledNode last = null;
        CreaturePartDef lastPart = null;
        for (int i = 0; i < repeat; i++) {
            Matrix4 local = socketMatrix(anchorSocket, false, anchor.scale);
            last = newNode(chosen, local, parentNode.scale, false, spec);
            anchor.children.add(last);
            last.worldTransform.set(anchor.worldTransform).mul(last.localTransform);
            lastPart = chosen;
            if (i + 1 < repeat) {
                anchor = last;
                anchorSocket = chosen.findSocket(tpl.continuationSocketId);
                if (anchorSocket == null) throw new IllegalStateException(
                    "repeat chain on '" + arch.id + "' missing continuation socket '"
                    + tpl.continuationSocketId + "' on part '" + chosen.id + "'");
            }
        }

        // Mirror copy (same chosen variant) across YZ plane, attached to the same parent socket.
        if (tpl.mirror && socket.mirrorGroup != null) {
            Matrix4 mlocal = socketMatrix(socket, true, parentNode.scale);
            AssembledNode m = newNode(chosen, mlocal, parentNode.scale, true, spec);
            parentNode.children.add(m);
            m.worldTransform.set(parentNode.worldTransform).mul(m.localTransform);
            // children of a mirrored attachment recurse under the mirror instance
            attachChildren(tpl, m, chosen, arch, rng, spec);
        }

        // Children recurse under the last (non-mirrored) instance.
        attachChildren(tpl, last, lastPart, arch, rng, spec);
    }

    private Matrix4 socketMatrix(Socket socket, boolean mirrored, float scale) {
        Vector3 p = new Vector3(socket.localPosition).scl(scale);
        if (mirrored) p.x = -p.x;
        return new Matrix4().set(p, socket.localRotation);
    }

    /** Deterministic variant pick: stable id-sorted list, indexed by rng. Throws if none eligible. */
    private CreaturePartDef pick(PartType type, BodyPlanArchetypeDef arch, Random rng) {
        return pick(type, arch, rng, null);
    }

    /**
     * Deterministic variant pick. When {@code requiredSocketId} is non-null the candidate list is
     * restricted to parts that expose that socket — used for repeat chains so a chosen segment is
     * guaranteed to provide its continuation socket. Stable id-sorted, indexed by rng.
     */
    private CreaturePartDef pick(PartType type, BodyPlanArchetypeDef arch, Random rng,
                                 String requiredSocketId) {
        List<CreaturePartDef> options = registry.partsFor(type, arch.bodyPlan);
        if (requiredSocketId != null) {
            List<CreaturePartDef> filtered = new java.util.ArrayList<>();
            for (CreaturePartDef p : options)
                if (p.findSocket(requiredSocketId) != null) filtered.add(p);
            options = filtered;
        }
        if (options.isEmpty()) throw new IllegalStateException(
            "No eligible part of type " + type + " for body plan " + arch.bodyPlan
            + (requiredSocketId == null ? "" : " exposing socket '" + requiredSocketId + "'"));
        return options.get(rng.nextInt(options.size()));
    }

    private AssembledNode newNode(CreaturePartDef part, Matrix4 local, float scale,
                                  boolean mirrored, CreatureSpec spec) {
        AssembledNode n = new AssembledNode();
        n.part = part;
        n.localTransform.set(local);
        n.worldTransform.set(local);   // root: world == local; overwritten by callers otherwise
        n.scale = scale;
        n.mirrored = mirrored;
        spec.allNodes.add(n);
        return n;
    }

    private void computeBounds(CreatureSpec spec) {
        spec.bounds.inf();
        Vector3 t = new Vector3();
        for (AssembledNode n : spec.allNodes) {
            n.worldTransform.getTranslation(t);
            float r = n.part.geometry.radius * n.scale;
            float len = n.part.geometry.length * n.scale;
            spec.bounds.ext(new Vector3(t).add(r, r, r));
            spec.bounds.ext(new Vector3(t).add(-r, -r, -r));
            spec.bounds.ext(new Vector3(t).add(0, 0, len));
        }
    }
}
