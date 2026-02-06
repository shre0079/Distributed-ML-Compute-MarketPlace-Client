package com.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AgentMain {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {

        System.out.println("Agent starting...");

        while(true) {
            pingServer();
            Thread.sleep(5000);
        }
    }

    private static void pingServer() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/ping"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Server response: " + response.body());
    }

    private static void register() throws Exception {
        WorkerInfo info = SystemInfo.getWorkerInfo();

        ObjecterMapper mapper=new ObjectMapper();
        String json=mapper.writeValueAsString(info);

        HttpRequest request=HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.send(request,HttpResponse.BodyHandlers.ofString());
        System.out.println("Registered worker: "+info.wokerId);
    }
}
