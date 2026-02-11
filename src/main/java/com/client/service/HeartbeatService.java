package com.client.service;

import com.client.dto.Heartbeat;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HeartbeatService {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void send(String workerId, String status) throws IOException, InterruptedException {

        Heartbeat heartbeat = new Heartbeat(workerId, status);
        String json= mapper.writeValueAsString(heartbeat);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/workers/heartbeat"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
