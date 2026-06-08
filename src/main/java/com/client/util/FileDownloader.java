package com.client.util;


import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

//public class FileDownloader {
//
//    private static final HttpClient httpClient = HttpClient.newHttpClient();
//
//    public static Path download(String url, String jobId) throws Exception {
//
//        Path dir = Path.of("jobs",jobId);
//        Files.createDirectories(dir);
//
//        Path filePath = dir.resolve("input.txt");
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(url))
//                .GET()
//                .build();
//
//        HttpResponse<InputStream> response = httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());
//
//        Files.copy(response.body(), filePath, StandardCopyOption.REPLACE_EXISTING);
//
//
//        return dir;
//    }
//}


//extract exact file name

public class FileDownloader {

    public static Path download(String url, String jobId) throws Exception {

        // Extract actual filename from URL
        String fileName = url.substring(url.lastIndexOf("/") + 1);

        // Fallback if URL has no filename segment
        if (fileName.isBlank()) {
            fileName = "input.bin";
        }

        Path jobDir = Path.of("jobs", jobId);
        Files.createDirectories(jobDir);

        Path filePath = jobDir.resolve(fileName);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download dataset: HTTP " + response.statusCode());
        }

        Files.write(filePath, response.body());

        System.out.println("Downloaded: " + fileName + " → " + filePath);

        return jobDir;
    }
}