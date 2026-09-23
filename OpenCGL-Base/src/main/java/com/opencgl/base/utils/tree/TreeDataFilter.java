package com.opencgl.base.utils.tree;

import com.opencgl.base.model.BaseDataDto;
import java.util.*;

/** In-memory filtering with separate ancestor/descendant visits (ancestors must not hide descendants). */
final class TreeDataFilter {
    private TreeDataFilter() {}

    static <T extends BaseDataDto> List<T> filter(List<T> data, String keyword) {
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        Map<Long, T> byId = new LinkedHashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (T item : data) {
            if (item == null || item.getId() == null || byId.putIfAbsent(item.getId(), item) != null) continue;
            children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item.getId());
        }
        Set<Long> visible = new HashSet<>(), parentsVisited = new HashSet<>(), descendantsVisited = new HashSet<>();
        Deque<Long> descendants = new ArrayDeque<>();
        for (T item : byId.values()) {
            if (item.getName() == null || !item.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            descendants.add(item.getId());
            T parent = item;
            while (parent != null && parentsVisited.add(parent.getId())) {
                visible.add(parent.getId());
                parent = byId.get(parent.getParentId());
            }
        }
        while (!descendants.isEmpty()) {
            Long id = descendants.removeFirst();
            if (!descendantsVisited.add(id)) continue;
            visible.add(id);
            descendants.addAll(children.getOrDefault(id, List.of()));
        }
        return byId.values().stream().filter(item -> visible.contains(item.getId())).toList();
    }
}
