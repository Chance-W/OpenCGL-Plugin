package com.opencgl.base.service;

import java.util.List;

import com.opencgl.base.model.HistoryItem;

/**
 * Interface for plugins to provide history capabilities.
 */
public interface HistoryService {
    /**
     * Retrieve history items.
     * @return List of history items.
     */
    List<? extends HistoryItem> getHistory();

    /**
     * Clear all history.
     */
    void clearHistory();

    /**
     * Delete a specific history item.
     * @param item The item to delete.
     */
    void deleteHistory(HistoryItem item);
}
