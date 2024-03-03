package com.xtool.opencgl.impl;


import com.opencgl.base.service.SendMessageService;
import com.xtool.opencgl.model.RestRequest;
import com.xtool.opencgl.model.RestResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RestSender implements SendMessageService<RestRequest, RestResponse> {
    private final Logger logger = LoggerFactory.getLogger(RestSender.class);

    @Override
    public RestResponse send(RestRequest restRequest) throws Exception {
        logger.info("begin rest request and the requestInfo is {}", restRequest);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build();
        Request request;
        RequestBody rbody = RequestBody.create(MediaType.parse(restRequest.getMediaType()), restRequest.getRequestMessage());
        request = new Request.Builder().url(restRequest.getRequestUrl()).post(rbody).build();

        Response response = client.newCall(request).execute();
        return RestResponse.builder().resultCode((long) response.code()).resultMsg(Objects.requireNonNull(response.body()).string()).build();
    }
}
