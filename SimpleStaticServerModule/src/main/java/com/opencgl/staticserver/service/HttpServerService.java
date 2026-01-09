package com.opencgl.staticserver.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP静态文件服务
 */
public class HttpServerService {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerService.class);
    
    private HttpServer server;
    private ExecutorService executor;
    private int port;
    private String bindAddress = "0.0.0.0";
    private List<Path> rootDirs;
    private boolean running = false;
    
    public HttpServerService() {
        this.rootDirs = new ArrayList<>();
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public void setBindAddress(String address) {
        this.bindAddress = address != null ? address : "0.0.0.0";
    }
    
    public void setRootDirs(List<String> dirs) {
        this.rootDirs.clear();
        for (String dir : dirs) {
            this.rootDirs.add(Paths.get(dir));
        }
    }
    
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("服务器已在运行中");
        }
        if (rootDirs.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个目录");
        }
        
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", new StaticFileHandler());
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "opencgl-static-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        running = true;
        String displayIp = bindAddress.equals("0.0.0.0") ? getLocalIp() : bindAddress;
        logger.info("静态文件服务器已启动: http://{}:{}", displayIp, port);
    }
    
    public void stop() {
        HttpServer serverToStop = server;
        ExecutorService executorToStop = executor;
        server = null;
        executor = null;
        running = false;
        if (serverToStop != null) {
            try {
                serverToStop.stop(0);
            } catch (RuntimeException e) {
                logger.error("停止 HTTP Server 失败", e);
            }
        }
        if (executorToStop != null) {
            executorToStop.shutdownNow();
            logger.info("静态文件服务器已停止");
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public String getAccessUrl() {
        String displayIp = bindAddress.equals("0.0.0.0") ? getLocalIp() : bindAddress;
        return "http://" + displayIp + ":" + port;
    }
    
    /**
     * 获取本机局域网IP
     */
    public static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    /**
     * 静态文件处理器
     */
    private class StaticFileHandler implements HttpHandler {
        private static final Map<String, String> MIME_TYPES = new HashMap<>();
        
        static {
            MIME_TYPES.put(".html", "text/html; charset=utf-8");
            MIME_TYPES.put(".htm", "text/html; charset=utf-8");
            MIME_TYPES.put(".css", "text/css; charset=utf-8");
            MIME_TYPES.put(".js", "application/javascript; charset=utf-8");
            MIME_TYPES.put(".json", "application/json; charset=utf-8");
            MIME_TYPES.put(".xml", "application/xml; charset=utf-8");
            MIME_TYPES.put(".txt", "text/plain; charset=utf-8");
            MIME_TYPES.put(".png", "image/png");
            MIME_TYPES.put(".jpg", "image/jpeg");
            MIME_TYPES.put(".jpeg", "image/jpeg");
            MIME_TYPES.put(".gif", "image/gif");
            MIME_TYPES.put(".svg", "image/svg+xml");
            MIME_TYPES.put(".ico", "image/x-icon");
            MIME_TYPES.put(".woff", "font/woff");
            MIME_TYPES.put(".woff2", "font/woff2");
            MIME_TYPES.put(".ttf", "font/ttf");
            MIME_TYPES.put(".pdf", "application/pdf");
            MIME_TYPES.put(".zip", "application/zip");
            MIME_TYPES.put(".mp4", "video/mp4");
            MIME_TYPES.put(".mp3", "audio/mpeg");
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // URL解码
            path = URLDecoder.decode(path, "UTF-8");

            // 拦截 POST 上传请求
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleUpload(exchange, path);
                return;
            }
            
            // 安全检查：防止目录遍历攻击
            if (path.contains("..")) {
                sendError(exchange, 403, "Forbidden");
                return;
            }
            
            // 处理根目录请求
            if (path.equals("/") || path.equals("/index.html")) {
                // 只有一个目录时直接显示该目录内容
                if (rootDirs.size() == 1) {
                    sendDirectoryListing(exchange, rootDirs.get(0), "/", 0);
                } else {
                    sendRootListing(exchange);
                }
                return;
            }
            
            // 检查是否是目录索引路径: /0/, /1/, /0/subdir/
            if (path.matches("^/\\d+/.*") || path.matches("^/\\d+$")) {
                int slashIdx = path.indexOf('/', 1);
                String indexStr = slashIdx > 0 ? path.substring(1, slashIdx) : path.substring(1);
                String subPath = slashIdx > 0 ? path.substring(slashIdx) : "/";
                
                try {
                    int dirIndex = Integer.parseInt(indexStr);
                    if (dirIndex >= 0 && dirIndex < rootDirs.size()) {
                        Path rootDir = rootDirs.get(dirIndex);
                        String relativePath = subPath.equals("/") ? "" : subPath.substring(1);
                        Path filePath = rootDir.resolve(relativePath);
                        
                        if (Files.exists(filePath)) {
                            if (Files.isDirectory(filePath)) {
                                sendDirectoryListing(exchange, filePath, path, dirIndex);
                            } else {
                                sendFile(exchange, filePath);
                            }
                            return;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            
            // 单目录模式：直接在第一个目录中查找
            if (rootDirs.size() == 1) {
                Path filePath = rootDirs.get(0).resolve(path.substring(1));
                if (Files.exists(filePath)) {
                    if (Files.isDirectory(filePath)) {
                        sendDirectoryListing(exchange, filePath, path, 0);
                    } else {
                        sendFile(exchange, filePath);
                    }
                    return;
                }
            }
            
            sendError(exchange, 404, "File Not Found: " + path);
        }
        
        private void sendFile(HttpExchange exchange, Path filePath) throws IOException {
            String ext = getFileExtension(filePath.toString());
            String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            
            byte[] data = Files.readAllBytes(filePath);
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
        
        private void sendDirectoryListing(HttpExchange exchange, Path dir, String urlPath, int dirIndex) throws IOException {
            StringBuilder html = new StringBuilder();
            String displayPath = rootDirs.size() == 1 ? urlPath : dir.toString();
            
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
            html.append("<title>目录: ").append(displayPath).append("</title>");
            html.append("<style>body{font-family:Arial,sans-serif;margin:20px;background:#f5f5f5;}");
            html.append("h2{color:#2c3e50;}");
            html.append("a{text-decoration:none;color:#0066cc;}a:hover{text-decoration:underline;}");
            html.append(".file{padding:8px 15px;display:block;background:white;margin:5px 0;border-radius:4px;box-shadow:0 1px 3px rgba(0,0,0,0.1);}.dir{font-weight:bold;}");
            html.append(".upload-box{background:white;padding:15px;border-radius:4px;box-shadow:0 1px 3px rgba(0,0,0,0.1);margin-bottom:20px;}");
            html.append("</style></head>");
            html.append("<body><h2>📁 ").append(displayPath).append("</h2>");

            // 优化版上传表单 (原生 JS 实现选完即传与重复检测)
            html.append("<div class='upload-box'>");
            html.append("<input type='file' id='fileInput' multiple style='display:none' onchange='handleFiles(this.files)'>");
            html.append("<button class='upload-btn' onclick='document.getElementById(\"fileInput\").click()'>📤 点击选择文件上传至此处 (支持多选)</button>");
            html.append("<div id='uploadStatus' style='margin-top:10px;font-size:14px;color:#666;'></div>");
            html.append("</div>");
            
            // 注入脚本和覆盖样式
            html.append("<style>");
            html.append(".upload-btn{background:#4CAF50;color:white;border:none;padding:10px 20px;border-radius:4px;cursor:pointer;font-size:14px;transition:background 0.3s;}");
            html.append(".upload-btn:hover{background:#45a049;}");
            html.append(".upload-btn:disabled{background:#cccccc;cursor:not-allowed;}");
            html.append("</style>");
            
            html.append("<script>");
            html.append("function handleFiles(files) {");
            html.append("  if(!files || files.length === 0) return;");
            html.append("  let existingFiles = Array.from(document.querySelectorAll('a.file:not(.dir)')).map(a => a.innerText.replace('📄 ', '').trim());");
            html.append("  let conflicts = [];");
            html.append("  for(let i=0; i<files.length; i++){");
            html.append("    if(existingFiles.includes(files[i].name)) conflicts.push(files[i].name);");
            html.append("  }");
            html.append("  if(conflicts.length > 0) {");
            html.append("    if(!confirm('检测到以下文件已存在，是否覆盖？\\n' + conflicts.join('\\n'))) return;");
            html.append("  }");
            html.append("  uploadFiles(files);");
            html.append("}");
            html.append("function uploadFiles(files) {");
            html.append("  let formData = new FormData();");
            html.append("  for(let i=0; i<files.length; i++){ formData.append('file', files[i]); }");
            html.append("  let btn = document.querySelector('.upload-btn');");
            html.append("  let status = document.getElementById('uploadStatus');");
            html.append("  btn.disabled = true; btn.innerText = '⏳ 上传中...';");
            html.append("  status.innerHTML = '<div style=\"width:100%;background:#e0e0e0;border-radius:4px;height:12px;margin-bottom:8px;overflow:hidden;\"><div id=\"progressBar\" style=\"width:0%;background:#4CAF50;height:100%;transition:width 0.2s\"></div></div><div id=\"progressText\" style=\"color:#333;\">准备上传...</div>';");
            html.append("  let xhr = new XMLHttpRequest();");
            html.append("  xhr.open('POST', '', true);");
            html.append("  xhr.upload.onprogress = function(e) {");
            html.append("    if(e.lengthComputable) {");
            html.append("      let percent = Math.round((e.loaded / e.total) * 100);");
            html.append("      document.getElementById('progressBar').style.width = percent + '%';");
            html.append("      document.getElementById('progressText').innerText = '当前上传进度: ' + percent + '%';");
            html.append("    }");
            html.append("  };");
            html.append("  xhr.onload = function() {");
            html.append("    if(xhr.status === 200 || xhr.status === 302) {");
            html.append("      document.getElementById('progressText').innerHTML = '<span style=\"color:#4CAF50\"><b>✅ 上传成功！视图即将在 1.5 秒后刷新...</b></span>';");
            html.append("      setTimeout(() => window.location.reload(), 1500);");
            html.append("    } else {");
            html.append("      document.getElementById('progressText').innerHTML = '<span style=\"color:red\">❌ 上传失败: HTTP ' + xhr.status + '</span>';");
            html.append("      btn.disabled = false; btn.innerText = '📤 点击选择文件上传至此处 (支持多选)';");
            html.append("    }");
            html.append("  };");
            html.append("  xhr.onerror = function() {");
            html.append("    status.innerHTML = '<span style=\"color:red\">❌ 网络请求中止或发生异常</span>';");
            html.append("    btn.disabled = false; btn.innerText = '📤 点击选择文件上传至此处 (支持多选)';");
            html.append("  };");
            html.append("  xhr.send(formData);");
            html.append("}");
            html.append("</script>");
            
            // 上级目录链接
            if (rootDirs.size() > 1 && urlPath.equals("/")) {
                // 多目录模式下的根目录，不显示上级
            } else if (urlPath.equals("/") || (rootDirs.size() > 1 && urlPath.matches("^/\\d+/?$"))) {
                if (rootDirs.size() > 1) {
                    html.append("<a class='file' href='/'>⬆️ 返回目录列表</a>");
                }
            } else {
                html.append("<a class='file' href='..'>⬆️ 上级目录</a>");
            }
            
            try (var stream = Files.list(dir)) {
                stream.sorted((a, b) -> {
                    boolean aDir = Files.isDirectory(a);
                    boolean bDir = Files.isDirectory(b);
                    if (aDir != bDir) return aDir ? -1 : 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                }).forEach(p -> {
                    String name = p.getFileName().toString();
                    String hrefSegment = encodeUriPathSegment(name);
                    boolean isDir = Files.isDirectory(p);
                    String icon = isDir ? "📁" : "📄";
                    String cssClass = isDir ? "file dir" : "file";
                    html.append("<a class='").append(cssClass).append("' href='")
                        .append(hrefSegment).append(isDir ? "/" : "").append("'>")
                        .append(icon).append(" ").append(escapeHtml(name)).append("</a>");
                });
            }
            
            html.append("</body></html>");
            sendHtml(exchange, html.toString());
        }
        
        private void sendRootListing(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
            html.append("<title>静态文件服务器</title>");
            html.append("<style>body{font-family:Arial,sans-serif;margin:20px;background:#f5f5f5;}");
            html.append(".card{background:white;padding:20px;border-radius:8px;margin:10px 0;box-shadow:0 2px 4px rgba(0,0,0,0.1);}");
            html.append("a{text-decoration:none;color:#0066cc;}a:hover{text-decoration:underline;}");
            html.append("h1{color:#333;}</style></head>");
            html.append("<body><h1>🌐 静态文件服务器</h1>");
            html.append("<p>服务目录列表:</p>");
            
            for (int i = 0; i < rootDirs.size(); i++) {
                Path dir = rootDirs.get(i);
                html.append("<div class='card'>");
                html.append("<a href='/").append(i).append("/'>📁 ").append(dir.toString()).append("</a>");
                html.append("</div>");
            }
            
            html.append("</body></html>");
            sendHtml(exchange, html.toString());
        }

        private void handleUpload(HttpExchange exchange, String path) throws IOException {
            Path targetDir = resolveRequestPath(path);
            if (targetDir == null || !Files.isDirectory(targetDir)) {
                sendError(exchange, 400, "Bad Request: Invalid target directory");
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                sendError(exchange, 400, "Bad Request: Content-Type must be multipart/form-data");
                return;
            }

            String boundaryStr = contentType.substring(contentType.indexOf("boundary=") + 9);
            // HTTP boundaries in body are prefixed with --
            byte[] boundary = ("--" + boundaryStr).getBytes("ISO-8859-1");

            try (InputStream is = exchange.getRequestBody()) {
                // A robust raw multipart parser
                ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
                int b;
                
                // 1. Skip until we read the FIRST boundary (handles preamble)
                boolean boundaryFound = false;
                while ((b = is.read()) != -1) {
                    headerBuffer.write(b);
                    if (endsWith(headerBuffer.toByteArray(), boundary)) {
                        boundaryFound = true;
                        break;
                    }
                }
                if (!boundaryFound) {
                    return; // EOF reached before any boundary
                }
                
                // Consume newline after boundary (or -- if it's the final boundary)
                int next1 = is.read();
                int next2 = is.read();
                if (next1 == '-' && next2 == '-') {
                    return; // Empty payload, just the end boundary
                }
                
                // Keep processing parts until EOF or end boundary
                while (true) {
                    // 2. Read headers for THIS part
                    headerBuffer.reset();
                    String filename = null;
                    boolean hasHeader = false;
                    while ((b = is.read()) != -1) {
                        headerBuffer.write(b);
                        if (endsWith(headerBuffer.toByteArray(), "\r\n\r\n".getBytes("ISO-8859-1"))) {
                            hasHeader = true;
                            String headerStr = new String(headerBuffer.toByteArray(), "UTF-8");
                            int start = headerStr.indexOf("filename=\"");
                            if (start != -1) {
                                start += 10;
                                int end = headerStr.indexOf("\"", start);
                                if (end != -1) {
                                    filename = headerStr.substring(start, end);
                                }
                            }
                            break;
                        }
                    }
                    if (!hasHeader) {
                        break; // EOF reached during header read
                    }

                    // 3. Read body data
                    if (filename != null && !filename.isEmpty()) {
                        filename = new File(filename).getName();
                        Path targetFile = targetDir.resolve(filename);
                        
                        try (OutputStream os = Files.newOutputStream(targetFile)) {
                            byte[] boundNewline = ("\r\n--" + boundaryStr).getBytes("ISO-8859-1");
                            int matchIndex = 0;
                            while ((b = is.read()) != -1) {
                                if (b == boundNewline[matchIndex]) {
                                    matchIndex++;
                                    if (matchIndex == boundNewline.length) {
                                        break; // End of file content reached
                                    }
                                } else {
                                    if (matchIndex > 0) {
                                        // We partially matched the boundary, write out the falsely matched bytes
                                        os.write(boundNewline, 0, matchIndex);
                                        // Then check if the current byte matches the FIRST char of boundary
                                        if (b == boundNewline[0]) {
                                            matchIndex = 1;
                                        } else {
                                            matchIndex = 0;
                                            os.write(b);
                                        }
                                    } else {
                                        os.write(b);
                                    }
                                }
                            }
                            os.flush();
                        }
                        
                        if (b == -1) break; // EOF
                        int check1 = is.read();
                        int check2 = is.read();
                        if (check1 == '-' && check2 == '-') {
                           break; // End
                        }
                        if (check1 == -1 || check2 == -1) break; // EOF
                    } else {
                        // If no filename, it's a standard form field, just skip until next boundary
                        // (Same State Machine matcher to simply discard bytes)
                        byte[] boundNewline = ("\r\n--" + boundaryStr).getBytes("ISO-8859-1");
                        int matchIndex = 0;
                        while ((b = is.read()) != -1) {
                            if (b == boundNewline[matchIndex]) {
                                matchIndex++;
                                if (matchIndex == boundNewline.length) {
                                    break;
                                }
                            } else {
                                if (matchIndex > 0) {
                                    if (b == boundNewline[0]) matchIndex = 1;
                                    else matchIndex = 0;
                                }
                            }
                        }
                        if (b == -1) break; // EOF
                        int check1 = is.read();
                        int check2 = is.read();
                        if (check1 == '-' && check2 == '-') {
                           break; // End
                        }
                        if (check1 == -1 || check2 == -1) break; // EOF
                    }
                }
            } catch (Exception e) {
                logger.error("Error parsing multipart upload", e);
                sendError(exchange, 500, "Upload Error: " + e.getMessage());
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, 2);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("OK".getBytes());
            }
        }

        private boolean endsWith(byte[] data, byte[] suffix) {
            if (data.length < suffix.length) return false;
            for (int i = 0; i < suffix.length; i++) {
                if (data[data.length - suffix.length + i] != suffix[i]) {
                    return false;
                }
            }
            return true;
        }

        private Path resolveRequestPath(String path) {
            if (path.equals("/") || path.equals("/index.html")) {
                if (rootDirs.size() == 1) {
                    return rootDirs.get(0);
                }
                return null; // Root listing does not support upload
            }

            if (path.matches("^/\\d+/.*") || path.matches("^/\\d+$")) {
                int slashIdx = path.indexOf('/', 1);
                String indexStr = slashIdx > 0 ? path.substring(1, slashIdx) : path.substring(1);
                String subPath = slashIdx > 0 ? path.substring(slashIdx) : "/";
                try {
                    int dirIndex = Integer.parseInt(indexStr);
                    if (dirIndex >= 0 && dirIndex < rootDirs.size()) {
                        Path rootDir = rootDirs.get(dirIndex);
                        String relativePath = subPath.equals("/") ? "" : subPath.substring(1);
                        return rootDir.resolve(relativePath);
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (rootDirs.size() == 1) {
                return rootDirs.get(0).resolve(path.substring(1));
            }

            return null;
        }
        
        private void sendHtml(HttpExchange exchange, String html) throws IOException {
            byte[] data = html.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
        
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String html = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
            byte[] data = html.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(code, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
        
        private String getFileExtension(String filename) {
            int idx = filename.lastIndexOf('.');
            return idx > 0 ? filename.substring(idx).toLowerCase() : "";
        }
    }

    /** 对 URL 路径段编码，避免空格、中文等导致 URISyntaxException / 下载失败 */
    private static String encodeUriPathSegment(String segment) {
        if (segment == null || segment.isEmpty()) return segment;
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 对目录列表中的文件名做简单 HTML 转义，防止 &lt; &amp; 等破坏页面 */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
