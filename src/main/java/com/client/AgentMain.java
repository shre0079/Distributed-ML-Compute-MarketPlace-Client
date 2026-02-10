package com.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.client.docker.DockerExecutor;
import com.client.dto.Job;
import com.client.dto.WorkerInfo;
import com.client.util.SystemInfo;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AgentMain {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

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
                .uri(URI.create("http://localhost:8080/jobs/poll"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204) {
            System.out.println("No job available...");
            return;
        }

        Job job = mapper.readValue(response.body(), Job.class);

        System.out.println("Downloading files...");
        Path jobDir = FileDownloader.download(job.fileUrl, job.jobId);

        System.out.println("Running container...");
        String logs = DockerExecutor.runContainer(job.dockerImage, jobDir.toAbsolutePath().toString());

        System.out.println("Uploading results..."+logs.length());
        ResultUploader.upload(job.jobId, logs);

        Path jobDir = null;

        try {
            jobDir = FileDownloader.download(job.fileUrl, job.jobId);

            System.out.println("Running container...");
            String logs = DockerExecutor.runContainer(
                    job.dockerImage,
                    jobDir.toAbsolutePath().toString()
            );

            System.out.println("Uploading results..."+logs.length());
            ResultUploader.upload(job.jobId, logs);

        } finally {
            if (jobDir != null) {
                CleanUpUtil.cleanup(jobDir);
            }
        }
    }
}
