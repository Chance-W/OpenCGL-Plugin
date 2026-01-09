package com.opencgl.base.utils;


import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class MyHttpHandler implements HttpHandler {
    private final Logger logger = LoggerFactory.getLogger(MyHttpHandler.class);
    private final String responseMessage;
    private final String responseHeader;
    private final TextArea outputText;

    public MyHttpHandler(String responseMessage, String responseHeader, TextArea outputText) {
        this.responseMessage = responseMessage;
        this.responseHeader = responseHeader;
        this.outputText = outputText;
    }

    @Override
    public void handle(HttpExchange httpExchange) {
        try {
            if (outputText != null) {
                outputText.append("remote requestIp is " + getRequestIp(httpExchange));
                outputText.append("remote request Header is " + getRequestHeader(httpExchange));
                outputText.append("remote request param is " + getRequestParam(httpExchange));
            }

            handleResponse(httpExchange);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 获取请求头
     */
    private String getRequestHeader(HttpExchange httpExchange) {
        Headers headers = httpExchange.getRequestHeaders();
        return headers.toString();
    }

    private String getRequestIp(HttpExchange httpExchange) {
        logger.info("request remote ip is {}", httpExchange.getRemoteAddress());
        return httpExchange.getRemoteAddress().toString();
    }

    /**
     * 获取请求参数
     */
    private String getRequestParam(HttpExchange httpExchange) throws Exception {
        String paramStr;
        if (httpExchange.getRequestMethod().equals("GET")) {
            //GET请求读queryString
            paramStr = httpExchange.getRequestURI().getQuery();
        } else {
            //非GET请求读请求体
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8));
            StringBuilder requestBodyContent = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                requestBodyContent.append(line);
            }
            paramStr = requestBodyContent.toString();
        }
        logger.info("request Param is {}", paramStr);
        return paramStr;

    }

    /**
     * 处理响应
     */
    private void handleResponse(HttpExchange httpExchange) throws Exception {
        byte[] responseContentByte = responseMessage.getBytes(StandardCharsets.UTF_8);
        //设置响应头，必须在sendResponseHeaders方法之前设置！
        //httpExchange.getResponseHeaders().add("Content-Type:", "text/html;charset=utf-8");
        httpExchange.getResponseHeaders().add("Content-Type:", responseHeader);
        //设置响应码和响应体长度，必须在getResponseBody方法之前调用！
        httpExchange.sendResponseHeaders(200, responseContentByte.length);
        OutputStream out = httpExchange.getResponseBody();
        out.write(responseContentByte);
        out.flush();
        out.close();
    }

    public static void main(String[] args) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8081), 0);
        //创建一个HttpContext，将路径为/myserver请求映射到MyHttpHandler处理器
        String responseContentStr = "<html>" +
                "<body>" +
                "responsetext" +
                "</body>" +
                "</html>";
        httpServer.createContext("/myserver", new MyHttpHandler(responseContentStr, "text/html;charset=utf-8", null));
        //设置服务器的线程池对象
        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        //启动服务器
        httpServer.start();
    }
}
