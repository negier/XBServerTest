package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class MultipartParser {

    private final InputStream inputStream;
    private final String boundary;

    public MultipartParser(InputStream inputStream, String boundary) {
        this.inputStream = inputStream;
        this.boundary = boundary;
    }

    public FilePart parse() throws IOException {
        // 跳过 boundary 和前导部分
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        // 跳过至开始部分的 boundary
        while ((line = reader.readLine()) != null) {
            if (line.contains(boundary)) {
                break;
            }
        }

        // 解析文件内容
        FilePart filePart = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("Content-Disposition: form-data;")) {
                String fileName = parseFileName(line);
                byte[] content = readContent(reader);
                filePart = new FilePart(fileName, content);
                break;
            }
        }
        return filePart;
    }

    private String parseFileName(String header) {
        // 提取文件名
        int fileNameIndex = header.indexOf("filename=\"");
        if (fileNameIndex != -1) {
            int startIndex = fileNameIndex + 10; // filename="
            int endIndex = header.indexOf("\"", startIndex);
            return header.substring(startIndex, endIndex);
        }
        return "unknown";
    }

    private byte[] readContent(BufferedReader reader) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals("")) {
                break;
            }
            outputStream.write(line.getBytes());
        }
        return outputStream.toByteArray();
    }

    public static class FilePart {
        private final String fileName;
        private final byte[] content;

        public FilePart(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }
    }
}

