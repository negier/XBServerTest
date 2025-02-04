package org.example;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XBServer implements AutoCloseable, HttpHandler {

    final HttpServer httpServer;
    final String host;
    final int port;
    final ExecutorService executorService;

    public XBServer(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()); // 创建一个线程池来处理并发请求
        this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.httpServer.createContext("/", this);
        this.httpServer.createContext("/download", this::handleDownload); // 新增文件下载路径
        this.httpServer.createContext("/upload", this::handleUpload); // 设置文件上传的路由
        this.httpServer.setExecutor(executorService); // 使用线程池处理请求
        this.httpServer.start();
        Logger.info("start xbserver at " + this.host + ":" + this.port);
    }

    // 文件下载功能
    private void handleDownload(HttpExchange exchange) throws IOException{
//        executorService.submit(() -> {
//            try {
                String path = exchange.getRequestURI().getPath();
                String fileName = path.substring(path.lastIndexOf("/") + 1);  // 获取URL中的文件名

                File file = new File(System.getProperty("user.dir"), fileName);

                if (!file.exists() || !file.isFile()) {
                    // 如果文件不存在，返回404
                    exchange.sendResponseHeaders(404, 0);
                    return;
                }

                long fileLength = file.length();
                long startByte = 0; // 默认从文件的开始部分开始下载

                // 获取请求头中的 Range
                String rangeHeader = exchange.getRequestHeaders().getFirst("Range");

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    // 格式：bytes=startByte-endByte
                    String[] range = rangeHeader.substring(6).split("-");
                    try {
                        startByte = Long.parseLong(range[0]);
                        // 如果提供了结束字节（可选），我们将其限制为文件长度
                        long endByte = (range.length > 1 && !range[1].isEmpty()) ? Long.parseLong(range[1]) : fileLength - 1;

                        if (startByte >= fileLength) {
                            // 如果请求的起始字节超出了文件大小，返回 416
                            exchange.sendResponseHeaders(416, 0);
                            return;
                        }

                        // 返回指定的部分文件
                        long contentLength = endByte - startByte + 1;
                        exchange.getResponseHeaders().set("Content-Range", "bytes " + startByte + "-" + endByte + "/" + fileLength);
                        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

                        // 设置状态码为206（部分内容）
                        exchange.sendResponseHeaders(206, contentLength);

                        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                             OutputStream os = exchange.getResponseBody()) {
                            raf.seek(startByte); // 跳到文件的指定位置
                            byte[] buffer = new byte[1024 * 64];  // 使用 64KB 的缓冲区
                            int bytesRead;
                            long bytesToRead = contentLength;

                            while (bytesToRead > 0 && (bytesRead = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                                os.write(buffer, 0, bytesRead);
                                bytesToRead -= bytesRead;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 如果 Range 格式不正确，返回 416
                        exchange.sendResponseHeaders(416, 0);
                    }
                } else {
                    // 如果没有 Range 请求头，则返回完整文件
                    String contentType = "application/octet-stream"; // 默认二进制文件
                    String contentDisposition = "attachment; filename=\"" + fileName + "\""; // 下载文件时的显示名称

                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Content-Disposition", contentDisposition);
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

                    // 设置响应头：文件大小
                    exchange.sendResponseHeaders(200, fileLength);

                    try (FileInputStream fis = new FileInputStream(file);
                         OutputStream os = exchange.getResponseBody()) {
                        byte[] buffer = new byte[1024 * 64];  // 64KB 的缓冲区
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
//            }catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
    }

/*    private void handleUpload(HttpExchange exchange) throws IOException {
        // 检查请求方法是否为 POST，只有 POST 方法可以上传文件。
        if ("POST".equals(exchange.getRequestMethod())) {
            // 解析multipart请求中的文件
            String boundary = extractBoundary(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (boundary == null) {
                exchange.sendResponseHeaders(400, 0);  // Bad Request
                return;
            }

            try (InputStream inputStream = exchange.getRequestBody()) {
                MultipartParser parser = new MultipartParser(inputStream, boundary);
                MultipartParser.FilePart filePart = parser.parse();

                if (filePart != null) {
                    // 文件保存到服务器
                    String fileName = filePart.getFileName();
                    File targetFile = new File("uploads", fileName);  // 文件保存到 uploads 目录
                    targetFile.getParentFile().mkdirs();  // 创建文件夹
                    try (FileOutputStream fileOut = new FileOutputStream(targetFile)) {
                        fileOut.write(filePart.getContent());
                    }

                    String response = "File uploaded successfully: " + fileName;
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } else {
                    exchange.sendResponseHeaders(400, 0);  // Bad Request
                }
            }
        } else {
            exchange.sendResponseHeaders(405, 0);  // Method Not Allowed
        }
    }*/

    private void handleUpload(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            // 获取请求头中的Range信息（如果有）
            String range = exchange.getRequestHeaders().getFirst("Range");
            long startByte = 0;
            boolean isRangeRequest = false;

            if (range != null && range.startsWith("bytes=")) {
                isRangeRequest = true;
                String[] rangeParts = range.split("=")[1].split("-");
                startByte = Long.parseLong(rangeParts[0]);
            }

            String boundary = extractBoundary(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (boundary == null) {
                exchange.sendResponseHeaders(400, 0);  // Bad Request
                return;
            }

            try (InputStream inputStream = exchange.getRequestBody()) {
                MultipartParser parser = new MultipartParser(inputStream, boundary);
                MultipartParser.FilePart filePart = parser.parse();

                if (filePart != null) {
                    // 获取文件名并计算文件的保存路径
                    String fileName = filePart.getFileName();
                    File targetFile = new File("uploads", fileName);
                    targetFile.getParentFile().mkdirs();  // 创建文件夹

                    // 处理断点续传
                    try (RandomAccessFile fileOut = new RandomAccessFile(targetFile, "rw")) {
                        if (isRangeRequest) {
                            fileOut.seek(startByte);  // 跳到断点处
                        }
                        fileOut.write(filePart.getContent());

                        // 返回上传进度
                        long fileLength = fileOut.length();
                        exchange.getResponseHeaders().set("Content-Range", "bytes " + startByte + "-" + (fileLength - 1) + "/" + fileLength);
                        exchange.sendResponseHeaders(200, 0);
                    }

                    String response = "File uploaded successfully: " + fileName;
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } else {
                    exchange.sendResponseHeaders(400, 0);  // Bad Request
                }
            }
        } else {
            exchange.sendResponseHeaders(405, 0);  // Method Not Allowed
        }
    }

    private String extractBoundary(String contentType) {
        if (contentType != null && contentType.contains("boundary=")) {
            return contentType.split("boundary=")[1];
        }
        return null;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String query = uri.getRawQuery();
        Logger.info(method + ": " + path + "?" + query);
        Headers respHeaders = exchange.getResponseHeaders();
        respHeaders.set("Content-Type", "text/html; charset=utf-8");
        respHeaders.set("Cache-Control", "no-cache");
        // 设置200响应:
        exchange.sendResponseHeaders(200, 0);
        String s = "<h1>Hello, world.</h1><p>" + LocalDateTime.now().withNano(0) + "</p>";
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void close() {
        this.httpServer.stop(3);
        this.executorService.shutdown(); // 关闭线程池
    }

}
