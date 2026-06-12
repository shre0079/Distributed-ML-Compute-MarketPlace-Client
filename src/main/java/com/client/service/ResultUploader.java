package com.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ResultUploader {

    static final String BASE_URL = "http://localhost:8080";

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void upload(String jobId, String logs, long runtimeMs, String workerSecret ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/jobs/result?jobId=" + jobId
                        + "&runtimeMs=" + runtimeMs
                        + "&workerSecret=" + workerSecret))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(logs))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static void uploadTimeout(String jobId, long runtimeMs, String logs, String workerSecret) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/jobs/timeout?jobId=" + jobId
                        + "&runtimeMs=" + runtimeMs
                        + "&workerSecret=" + workerSecret))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(logs))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Timeout report failed: HTTP " +
                    response.statusCode());
        }

        System.out.println("Timeout reported for job: " + jobId);
    }
}
