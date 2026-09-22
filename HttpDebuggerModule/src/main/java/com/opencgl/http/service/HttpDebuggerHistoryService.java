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
            HttpHistoryItem httpItem = (HttpHistoryItem) item;
            repository.delete(httpItem.getId());
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
        item.setRequestSnapshot(JSON.toJSONString(toSnapshot(request),
            com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect,
            com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        item.setResponseSnapshot(JSON.toJSONString(toSnapshot(response),
            com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect,
            com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        
        // add() is called by the HTTP request worker, so persist before
        // publishing the item to the UI. This avoids losing the record when
        // the plugin is closed immediately after a request completes.
        repository.save(item);
        javafx.application.Platform.runLater(() -> {
            if (disposed) return;
            historyList.add(0, item);
            if (historyList.size() > 100) {
                historyList.remove(100, historyList.size());
            }
        });
    }
    
    private void loadRecent() {
        // History is loaded while the service is constructed, before the
        // HistoryViewBuilder binds to the observable list. This matches the
        // synchronous loading behavior used by the Dubbo module and avoids a
        // race where the plugin is reopened before the background read updates
        // the newly created history view.
        historyList.setAll(repository.findRecent(100));
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
        // Writes are synchronous and therefore already durable before the
        // response is displayed; only the pending initial read is cancelled.
        for (Future<?> task : tasks) {
            task.cancel(true);
        }
        tasks.clear();
        try {
            historyExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private java.util.Map<String, Object> toSnapshot(HttpRequestModel request) {
        java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("method", request.getMethod());
        snap.put("url", request.getUrl());
        snap.put("headers", request.getHeaders());
        snap.put("params", request.getParams());
        snap.put("body", request.getBody());
        snap.put("bodyType", request.getBodyType());
        snap.put("timeout", request.getTimeout());
        snap.put("sslVerification", request.isSslVerification());
        snap.put("followRedirects", request.isFollowRedirects());
        snap.put("clientCertPath", request.getClientCertPath());
        snap.put("serverCertPath", request.getServerCertPath());
        return snap;
    }

    private java.util.Map<String, Object> toSnapshot(HttpResponseModel response) {
        java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("statusCode", response.getStatusCode());
        snap.put("statusMessage", response.getStatusMessage());
        snap.put("headers", response.getHeaders());
        snap.put("body", response.getBody());
        snap.put("responseTime", response.getResponseTime());
        snap.put("contentLength", response.getContentLength());
        snap.put("error", response.getError());
        return snap;
    }
}
