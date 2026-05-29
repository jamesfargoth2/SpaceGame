package com.galacticodyssey.npc.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogDataRegistry {

    private final Map<String, DialogTree> treesById = new HashMap<>();

    public void register(DialogTree tree) {
        treesById.put(tree.id, tree);
    }

    public DialogTree getTree(String id) {
        return treesById.get(id);
    }

    public List<String> getTreeIds() {
        return new ArrayList<>(treesById.keySet());
    }

    public void loadFromFiles() {
        JsonReader reader = new JsonReader();
        int loaded = 0;

        // Prefer manifest-based loading: individual file lookups work on the classpath
        // regardless of working directory (directory listing does not).
        FileHandle manifest = Gdx.files.internal("data/dialogues/manifest.json");
        if (manifest.exists()) {
            Gdx.app.log("Dialog", "DialogDataRegistry: loading via manifest");
            try {
                JsonValue arr = reader.parse(manifest);
                for (JsonValue entry = arr.child; entry != null; entry = entry.next) {
                    String id = entry.asString();
                    FileHandle file = Gdx.files.internal("data/dialogues/" + id + ".json");
                    try {
                        DialogTree tree = parseTree(reader.parse(file));
                        register(tree);
                        loaded++;
                        Gdx.app.log("Dialog", "DialogDataRegistry: loaded tree id='" + tree.id + "'");
                    } catch (Exception e) {
                        Gdx.app.error("Dialog", "DialogDataRegistry: failed to load " + id, e);
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("Dialog", "DialogDataRegistry: failed to parse manifest", e);
            }
        } else {
            // Fallback: scan directory (works in Gradle run where workingDir is set correctly)
            FileHandle dir = Gdx.files.internal("data/dialogues");
            Gdx.app.log("Dialog", "DialogDataRegistry: no manifest, scanning dir exists=" + dir.exists());
            if (dir.exists()) {
                for (FileHandle file : dir.list()) {
                    if (!file.name().endsWith(".json")) continue;
                    try {
                        DialogTree tree = parseTree(reader.parse(file));
                        register(tree);
                        loaded++;
                        Gdx.app.log("Dialog", "DialogDataRegistry: loaded tree id='" + tree.id + "'");
                    } catch (Exception e) {
                        Gdx.app.error("Dialog", "DialogDataRegistry: failed to load " + file.name(), e);
                    }
                }
            }
        }
        Gdx.app.log("Dialog", "DialogDataRegistry: total trees=" + loaded);
    }

    private DialogTree parseTree(JsonValue root) {
        DialogTree tree = new DialogTree();
        tree.id = root.getString("id");
        tree.startNodeId = root.getString("startNodeId");

        JsonValue nodesObj = root.get("nodes");
        for (JsonValue nodeEntry = nodesObj.child; nodeEntry != null; nodeEntry = nodeEntry.next) {
            DialogNode node = new DialogNode();
            node.id = nodeEntry.name;
            node.speakerLabel = nodeEntry.getString("speakerLabel");
            node.text = nodeEntry.getString("text");

            JsonValue choicesArr = nodeEntry.get("choices");
            if (choicesArr != null) {
                for (JsonValue choiceEntry = choicesArr.child; choiceEntry != null; choiceEntry = choiceEntry.next) {
                    DialogChoice choice = new DialogChoice();
                    choice.key = choiceEntry.getString("key");
                    choice.text = choiceEntry.getString("text");
                    choice.nextNodeId = choiceEntry.getString("nextNodeId", null);
                    node.choices.add(choice);
                }
            }

            tree.nodes.put(node.id, node);
        }

        return tree;
    }
}
