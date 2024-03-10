package com.opencgl.impl;



import com.opencgl.base.service.SendMessageService;
import com.opencgl.model.RestMethodEnum;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestResponse;
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
        if (RestMethodEnum.GET.equals(restRequest.getRequestMethod())) {
            String requestUrl = restRequest.getRequestUrl();
            StringBuilder paramsDataBuffer = new StringBuilder();
            {
                if (requestUrl.contains("?")) {
                    paramsDataBuffer.append("&");
                } else {
                    paramsDataBuffer.append("?");
                }
                paramsDataBuffer.deleteCharAt(paramsDataBuffer.length() - 1);
                requestUrl += paramsDataBuffer.toString();
                request = new Request.Builder().url(requestUrl).headers(Headers.of(restRequest.getRequestHeaderMap())).build();
            }
        } else {
            FormBody.Builder builder = new FormBody.Builder();
            RequestBody body = builder.build();
            switch (restRequest.getRequestMethod()) {
                case RestMethodEnum.POST:
                    RequestBody rbody = RequestBody.create(MediaType.parse(restRequest.getMediaType()), restRequest.getRequestMessage());
                    request = new Request.Builder().url(restRequest.getRequestUrl()).post(rbody).build();
                    break;
                case RestMethodEnum.HEAD:
                    request = new Request.Builder().url(restRequest.getRequestUrl()).head().headers(Headers.of(restRequest.getRequestHeaderMap())).build();
                    break;
                case RestMethodEnum.PUT:
                    request = new Request.Builder().url(restRequest.getRequestUrl()).put(body).headers(Headers.of(restRequest.getRequestHeaderMap())).build();
                    break;
                case RestMethodEnum.PATCH:
                    request = new Request.Builder().url(restRequest.getRequestUrl()).patch(body).headers(Headers.of(restRequest.getRequestHeaderMap())).build();
                    break;
                case RestMethodEnum.DELETE:
                    request = new Request.Builder().url(restRequest.getRequestUrl()).delete(body).headers(Headers.of(restRequest.getRequestHeaderMap())).build();
                    break;
                default:
                    return RestResponse.builder().resultMsg("类型暂不支持").build();
            }
        }
        Response response = client.newCall(request).execute();
        return RestResponse.builder().resultCode((long) response.code()).resultMsg(Objects.requireNonNull(response.body()).string()).build();
    }
}
