package com.opencgl.http.service;

import com.alibaba.fastjson.JSON;
import com.opencgl.http.model.HttpRequestModel;
import com.opencgl.http.model.HttpResponseModel;
import com.opencgl.http.model.HttpHistoryItem;
import com.opencgl.http.repository.HttpHistoryRepository;
import com.opencgl.http.repository.impl.HttpHistoryRepositoryImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Date;
import java.util.List;

import com.opencgl.base.model.HistoryItem;
import com.opencgl.base.service.HistoryService;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Request History Service
 */
public class HttpDebuggerHistoryService implements HistoryService {
    private final HttpHistoryRepository repository;
    private final ObservableList<HttpHistoryItem> historyList;
    private final ExecutorService historyExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "http-history-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> tasks = new CopyOnWriteArrayList<>();
    private volatile boolean disposed;

    public HttpDebuggerHistoryService() {
        this.repository = new HttpHistoryRepositoryImpl();
        this.historyList = FXCollections.observableArrayList();
        loadRecent();
    }

    @Override
    public List<? extends HistoryItem> getHistory() {
        return historyList;
    }

    @Override
    public void clearHistory() {
        clear();
    }

    @Override
    public void deleteHistory(HistoryItem item) {
        if (item instanceof HttpHistoryItem) {
            // Delete from DB not implemented in repo yet?
            // Actually repo interface has no delete(item)
            // But we can just remove from list for now or add it to repo
            historyList.remove(item);
        }
    }

    public void add(HttpRequestModel request, HttpResponseModel response) {
        if (disposed) return;
        HttpHistoryItem item = new HttpHistoryItem();
        item.setId(UUID.randomUUID().toString()); // Generate UUID
        item.setMethod(request.getMethod());
        item.setUrl(request.getUrl());
        item.setStatusCode(response.getStatusCode());
        item.setDuration(response.getResponseTime());
        item.setSize(response.getContentLength());
        item.setRequestTime(new Date());
        
        // Snapshots
        item.setRequestSnapshot(JSON.toJSONString(request));
        item.setResponseSnapshot(JSON.toJSONString(response));
        
        // Save async
        submit(() -> {
            repository.save(item);
            // Reload strictly on FX thread if needed, or just add to head?
            // To keep sync with DB limit, maybe simpler to add to list start
            // and reload if needed.
            javafx.application.Platform.runLater(() -> {
                if (disposed) return;
                historyList.add(0, item);
                if (historyList.size() > 100) {
                    historyList.remove(100, historyList.size());
                }
            });
        });
    }
    
    private void loadRecent() {
        submit(() -> {
            List<HttpHistoryItem> recent = repository.findRecent(100);
            javafx.application.Platform.runLater(() -> {
                if (!disposed) historyList.setAll(recent);
            });
        });
    }
    
    public void clear() {
        if (disposed) return;
        repository.clearAll();
        historyList.clear();
    }

    private void submit(Runnable action) {
        if (disposed) return;
        tasks.removeIf(Future::isDone);
        tasks.add(historyExecutor.submit(action));
    }

    public void close() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : tasks) {
            try {
                task.cancel(true);
            } catch (Exception ignored) {
            }
        }
        tasks.clear();
        try {
            historyExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }
}
