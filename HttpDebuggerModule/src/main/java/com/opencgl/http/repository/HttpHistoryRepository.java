package com.opencgl.http.repository;

import com.opencgl.http.model.HttpHistoryItem;
import java.util.List;

public interface HttpHistoryRepository {
    void initializeDatabase();
    void save(HttpHistoryItem item);
    List<HttpHistoryItem> findRecent(int limit);
    void clearAll();
}
