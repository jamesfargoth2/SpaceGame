package com.galacticodyssey.core.events;

public class InteractionPromptEvent {
    public final String promptText;
    public final boolean visible;
    public InteractionPromptEvent(String promptText, boolean visible) { this.promptText = promptText; this.visible = visible; }
}
