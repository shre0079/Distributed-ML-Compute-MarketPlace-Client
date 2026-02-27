package com.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import com.client.docker.DockerExecutor;
import com.client.dto.Job;
import com.client.dto.WorkerInfo;
import com.client.service.HeartbeatService;
import com.client.service.ResultUploader;
import com.client.util.CleanUpUtil;
import com.client.util.FileDownloader;
import com.client.util.SystemInfo;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AgentMain {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String workerId;

    public static void main(String[] args) throws Exception {

        System.out.println("Agent starting...");

        try {
            register();
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return;
        }

//        DockerExecutor.runContainer("hello-world");

        while(true) {
            try{
                HeartbeatService.send(workerId, "IDLE");
                pollJob();
            } catch (Exception e) {
                System.out.println("Server unreachable, retrying with error: "+e.getMessage());;
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
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
        workerId = info.workerId;
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
                .uri(URI.create("http://localhost:8080/{workerId}"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204) {
            System.out.println("No job available...");
            return;
        }

        Job job = mapper.readValue(response.body(), Job.class);

        Path jobDir = null;

        try {

            System.out.println("Downloading files...");
            jobDir = FileDownloader.download(job.fileUrl, job.jobId);

            System.out.println("Running container...");
            String logs = DockerExecutor.runContainer(
                    job.dockerImage,
                    jobDir.toAbsolutePath().toString()
            );

            Path outputDir = jobDir.resolve("output");
            Path zip = ZipUtil.zipFolder(outputDir, job.jobId + ".zip");
            ArtifactUploader.upload(job.jobId, zip);z

            System.out.println("Uploading results..."+logs.length());
            ResultUploader.upload(job.jobId, logs);

        } catch (Exception e) {
            System.out.println("Job failed: " + e.getMessage());
            reportFailure(job.jobId);
        }
        finally {
            if (jobDir != null) {
                CleanUpUtil.cleanup(jobDir);
            }
        }   
    }

    public static void reportFailure(String jobId) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/jobs/fail?jobId=" + jobId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public class ZipUtil {

        public static Path zipFolder(Path sourceFolder, String zipName) throws Exception {

            Path zipPath = sourceFolder.resolveSibling(zipName);

            try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipPath))) {

                Files.walk(sourceFolder)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            ZipEntry zipEntry = new ZipEntry(sourceFolder.relativize(path).toString());
                            try {
                                zs.putNextEntry(zipEntry);
                                Files.copy(path, zs);
                                zs.closeEntry();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }

            return zipPath;
        }
    }

    public class ArtifactUploader {

        private static final HttpClient client = HttpClient.newHttpClient();

        public static void upload(String jobId, Path zipPath) throws Exception {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/jobs/artifact?jobId=" + jobId))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofFile(zipPath))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Uploaded artifact for " + jobId);
        }
    }
}
