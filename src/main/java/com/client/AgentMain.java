package com.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.client.docker.DockerExecutor;
import com.client.dto.WorkerInfo;
import com.client.util.SystemInfo;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AgentMain {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {

        System.out.println("Agent starting...");

        register();

        DockerExecutor.runContainer("hello-world");

        while(true) {
            try{
                pollJob();
            } catch (Exception e) {
                System.out.println("Server unreachable, retrying with error: "+e.getMessage());;
            }

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

        ObjectMapper mapper = new ObjectMapper();
        String json=mapper.writeValueAsString(info);


        HttpRequest request=HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.send(request,HttpResponse.BodyHandlers.ofString());
        System.out.println("Registered worker: "+ info.workerId);
    }

    private static void pollJob() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:8080/jobs/poll"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,HttpResponse.BodyHandlers.ofString());

        Job job = mapper.readValue(response.body(), Job.class);

        if(job.dockerImage !=null){
            System.out.println("Received job --> " + job.dockerImage);

            DockerExecutor.runContainer(job.dockerImage);
        }
    }
}
