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
        FileHandle dir = Gdx.files.internal("data/dialogues");
        if (!dir.exists()) return;

        JsonReader reader = new JsonReader();
        for (FileHandle file : dir.list()) {
            if (!file.name().endsWith(".json")) continue;
            try {
                JsonValue root = reader.parse(file);
                DialogTree tree = parseTree(root);
                register(tree);
            } catch (Exception e) {
                Gdx.app.error("DialogDataRegistry", "Failed to load " + file.name(), e);
            }
        }
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
