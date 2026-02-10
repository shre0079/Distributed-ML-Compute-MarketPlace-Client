package com.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ResultUploader {
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void upload(String jobId, String logs) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/jobs/result?jobId=" + jobId))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(logs))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
