package com.opencgl.base.theme;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Identity-based weak registry for UI objects whose lifecycle belongs to JavaFX. */
final class WeakReferenceRegistry<T> {
    private final List<WeakReference<T>> references = new CopyOnWriteArrayList<>();

    void add(T value) {
        if (value == null || liveValues().stream().anyMatch(existing -> existing == value)) return;
        references.add(new WeakReference<>(value));
    }

    void remove(T value) {
        references.removeIf(reference -> {
            T existing = reference.get();
            return existing == null || existing == value;
        });
    }

    List<T> liveValues() {
        references.removeIf(reference -> reference.get() == null);
        return references.stream().map(WeakReference::get).filter(value -> value != null).toList();
    }

    boolean isEmpty() {
        return liveValues().isEmpty();
    }
}
