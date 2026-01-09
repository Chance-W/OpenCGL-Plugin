package com.opencgl.grpc.service;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 服务
 * 支持动态调用 gRPC 服务
 *
 * @author OpenCGL
 */
public class GrpcService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GrpcService.class);

    private ManagedChannel channel;
    private Descriptors.FileDescriptor fileDescriptor;
    private Map<String, Descriptors.ServiceDescriptor> services = new HashMap<>();
    private Map<String, Descriptors.MethodDescriptor> methods = new HashMap<>();

    /**
     * 连接到 gRPC 服务
     */
    public void connect(String host, int port, boolean usePlaintext) {
        close();
        
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (usePlaintext) {
            builder.usePlaintext();
        }
        
        channel = builder.build();
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (channel == null) return false;
        return !channel.isShutdown() && !channel.isTerminated();
    }

    /**
     * 从 .proto 文件描述加载服务定义
     * 注意：实际使用需要 protoc 编译生成的描述符文件
     */
    public void loadProtoDescriptor(byte[] descriptorBytes) throws Exception {
        DescriptorProtos.FileDescriptorSet fdSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes);
        
        services.clear();
        methods.clear();
        
        for (DescriptorProtos.FileDescriptorProto fdProto : fdSet.getFileList()) {
            Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                fdProto, 
                new Descriptors.FileDescriptor[0]
            );
            
            for (Descriptors.ServiceDescriptor svc : fd.getServices()) {
                services.put(svc.getFullName(), svc);
                for (Descriptors.MethodDescriptor method : svc.getMethods()) {
                    String fullMethod = svc.getFullName() + "/" + method.getName();
                    methods.put(fullMethod, method);
                }
            }
            
            this.fileDescriptor = fd;
        }
    }

    /**
     * 获取服务列表
     */
    public List<String> listServices() {
        return new ArrayList<>(services.keySet());
    }

    /**
     * 获取服务的方法列表
     */
    public List<MethodInfo> listMethods(String serviceName) {
        Descriptors.ServiceDescriptor svc = services.get(serviceName);
        if (svc == null) return Collections.emptyList();
        
        List<MethodInfo> result = new ArrayList<>();
        for (Descriptors.MethodDescriptor method : svc.getMethods()) {
            result.add(new MethodInfo(
                method.getName(),
                method.getFullName(),
                method.getInputType().getName(),
                method.getOutputType().getName(),
                method.isClientStreaming(),
                method.isServerStreaming()
            ));
        }
        return result;
    }

    /**
     * 调用 Unary 方法
     */
    public String callUnary(String serviceName, String methodName, String requestJson, int timeoutSeconds) 
            throws Exception {
        if (channel == null) throw new IllegalStateException("未连接");
        
        Descriptors.ServiceDescriptor svc = services.get(serviceName);
        if (svc == null) throw new IllegalArgumentException("服务不存在: " + serviceName);
        
        Descriptors.MethodDescriptor method = null;
        for (Descriptors.MethodDescriptor m : svc.getMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }
        if (method == null) throw new IllegalArgumentException("方法不存在: " + methodName);

        // 构建动态消息
        DynamicMessage.Builder reqBuilder = DynamicMessage.newBuilder(method.getInputType());
        JsonFormat.parser().ignoringUnknownFields().merge(requestJson, reqBuilder);
        DynamicMessage request = reqBuilder.build();

        // 构建方法描述符
        String fullMethodName = serviceName + "/" + methodName;
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = MethodDescriptor
            .<DynamicMessage, DynamicMessage>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethodName)
            .setRequestMarshaller(ProtoUtils.marshaller(
                DynamicMessage.getDefaultInstance(method.getInputType())))
            .setResponseMarshaller(ProtoUtils.marshaller(
                DynamicMessage.getDefaultInstance(method.getOutputType())))
            .build();

        // 执行调用
        CallOptions options = CallOptions.DEFAULT.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
        DynamicMessage response = ClientCalls.blockingUnaryCall(
            channel.newCall(grpcMethod, options), request);

        // 转换响应为 JSON
        return JsonFormat.printer().print(response);
    }

    /**
     * 获取请求消息模板
     */
    public String getRequestTemplate(String serviceName, String methodName) throws Exception {
        Descriptors.ServiceDescriptor svc = services.get(serviceName);
        if (svc == null) return "{}";
        
        for (Descriptors.MethodDescriptor method : svc.getMethods()) {
            if (method.getName().equals(methodName)) {
                DynamicMessage defaultMsg = DynamicMessage.getDefaultInstance(method.getInputType());
                return JsonFormat.printer().includingDefaultValueFields().print(defaultMsg);
            }
        }
        return "{}";
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
            }
            channel = null;
        }
    }

    public record MethodInfo(
        String name,
        String fullName,
        String inputType,
        String outputType,
        boolean clientStreaming,
        boolean serverStreaming
    ) {}
}
