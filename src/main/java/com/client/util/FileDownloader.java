package com.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDownloader {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static Path download(String url, String jobId) throws Exception {

        Path dir = Path.of("jobs",jobId);
        Files.createDirectories(dir);

        Path filePath = dir.resolve("input.txt");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());

        Files.copy(response.body(),filePath);

        return dir;
    }
}
