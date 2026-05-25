package com.galacticodyssey.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventBus {

    @FunctionalInterface
    public interface EventListener<T> {
        void onEvent(T event);
    }

    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public <T> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        var list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        var list = listeners.get(event.getClass());
        if (list != null) {
            for (var listener : list) {
                ((EventListener<T>) listener).onEvent(event);
            }
        }
    }
}
