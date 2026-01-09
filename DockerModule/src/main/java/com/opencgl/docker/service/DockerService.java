package com.opencgl.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Docker 服务
 * 支持容器和镜像管理
 *
 * @author OpenCGL
 */
public class DockerService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);

    private DockerClient dockerClient;

    /**
     * 连接到 Docker
     */
    public void connect(String dockerHost) {
        close();
        
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build();
        
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build();
        
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        if (dockerClient == null) return false;
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            logger.error("Docker 连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取 Docker 信息
     */
    public String getInfo() {
        if (dockerClient == null) return "未连接";
        Info info = dockerClient.infoCmd().exec();
        return String.format("Docker: %s\nContainers: %d (running: %d)\nImages: %d\nOS: %s",
            info.getServerVersion(),
            info.getContainers(),
            info.getContainersRunning(),
            info.getImages(),
            info.getOperatingSystem()
        );
    }

    // ==================== 容器管理 ====================

    /**
     * 列出容器
     */
    public List<ContainerInfo> listContainers(boolean showAll) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        
        List<Container> containers = dockerClient.listContainersCmd()
            .withShowAll(showAll)
            .exec();
        
        List<ContainerInfo> result = new ArrayList<>();
        for (Container c : containers) {
            result.add(new ContainerInfo(
                c.getId().substring(0, 12),
                c.getImage(),
                c.getNames() != null && c.getNames().length > 0 ? c.getNames()[0] : "",
                c.getState(),
                c.getStatus()
            ));
        }
        return result;
    }

    /**
     * 启动容器
     */
    public void startContainer(String containerId) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * 停止容器
     */
    public void stopContainer(String containerId) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        dockerClient.stopContainerCmd(containerId).exec();
    }

    /**
     * 重启容器
     */
    public void restartContainer(String containerId) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        dockerClient.restartContainerCmd(containerId).exec();
    }

    /**
     * 删除容器
     */
    public void removeContainer(String containerId, boolean force) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        dockerClient.removeContainerCmd(containerId).withForce(force).exec();
    }

    /**
     * 获取容器日志
     */
    public String getContainerLogs(String containerId, int tailLines) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        
        StringBuilder logs = new StringBuilder();
        try {
            dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tailLines)
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        logs.append(new String(frame.getPayload()));
                    }
                }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return logs.toString();
    }

    // ==================== 镜像管理 ====================

    /**
     * 列出镜像
     */
    public List<ImageInfo> listImages() {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        
        List<Image> images = dockerClient.listImagesCmd().exec();
        List<ImageInfo> result = new ArrayList<>();
        for (Image img : images) {
            String[] tags = img.getRepoTags();
            String tag = (tags != null && tags.length > 0) ? tags[0] : "<none>";
            result.add(new ImageInfo(
                img.getId().substring(7, 19),
                tag,
                formatSize(img.getSize()),
                img.getCreated()
            ));
        }
        return result;
    }

    /**
     * 删除镜像
     */
    public void removeImage(String imageId, boolean force) {
        if (dockerClient == null) throw new IllegalStateException("未连接");
        dockerClient.removeImageCmd(imageId).withForce(force).exec();
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void close() {
        DockerClient clientToClose = dockerClient;
        dockerClient = null;
        if (clientToClose != null) {
            try {
                clientToClose.close();
            } catch (Exception e) {
                logger.error("关闭 Docker 连接失败", e);
            }
        }
    }

    public record ContainerInfo(String id, String image, String name, String state, String status) {}
    public record ImageInfo(String id, String tag, String size, Long created) {}
}
