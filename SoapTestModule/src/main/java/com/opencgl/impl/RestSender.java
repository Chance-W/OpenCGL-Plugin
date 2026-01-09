package com.opencgl.impl;


import com.opencgl.base.service.SendMessageService;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RestSender implements SendMessageService<RestRequest, RestResponse>, AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(RestSender.class);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build();

    @Override
    public RestResponse send(RestRequest restRequest) throws Exception {
        logger.info("begin rest request and the requestInfo is {}", restRequest);
        Request request;
        RequestBody rbody = RequestBody.create(MediaType.parse(restRequest.getMediaType()), restRequest.getRequestMessage());
        request = new Request.Builder().url(restRequest.getRequestUrl()).post(rbody).build();

        try (Response response = client.newCall(request).execute()) {
            return RestResponse.builder().resultCode((long) response.code()).resultMsg(Objects.requireNonNull(response.body()).string()).build();
        }
    }

    @Override
    public void close() {
        try {
            client.dispatcher().cancelAll();
        } catch (Exception ignored) {
        }
        try {
            client.dispatcher().executorService().shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            client.connectionPool().evictAll();
        } catch (Exception ignored) {
        }
        if (client.cache() != null) {
            try {
                client.cache().close();
            } catch (Exception ignored) {
            }
        }
    }
}
