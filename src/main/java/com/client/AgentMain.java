package com.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    static final String BASE_URL = "http://localhost:8080";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String workerId;

    public static void main(String[] args) throws Exception {

        System.out.println("Agent starting...");

        AgentMain agent = new AgentMain();

        try {
            pingServer();       // verify backend is up before doing anything
            agent.register();   // then register
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return;
        }

//        DockerExecutor.runContainer("hello-world");

        while (true) {
            try {
                HeartbeatService.send(workerId, "IDLE");
                agent.pollJob();
            } catch (Exception e) {
                System.out.println("Error in main loop: " + e.getMessage());
            }
            Thread.sleep(5000);
        }
    }

    private static void pingServer() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/ping"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Backend unreachable, ping failed: HTTP " + response.statusCode());
        }

        System.out.println("Backend reachable: " + response.body());
    }

//    private static void register() throws Exception {
//        WorkerInfo info = SystemInfo.getWorkerInfo();
//        workerId = info.workerId;
//        ObjectMapper mapper = new ObjectMapper();
//        String json=mapper.writeValueAsString(info);
//
//
//        HttpRequest request=HttpRequest.newBuilder()
//                .uri(URI.create("http://localhost:8080/register"))
//                .header("Content-Type", "application/json")
//                .POST(HttpRequest.BodyPublishers.ofString(json))
//                .build();
//
//        httpClient.send(request,HttpResponse.BodyHandlers.ofString());
//        System.out.println("Registered worker: "+ info.workerId);
//    }

    //register method wrt MAC+HOSTNAME + added validation and retry logic
    private void register() throws Exception {

        WorkerInfo info = SystemInfo.getWorkerInfo();
        this.workerId = info.workerId;

        String json = mapper.writeValueAsString(info);

        int maxAttempts = 5;
        int delayMs = 3000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    System.out.println("Registered successfully as: " + this.workerId);
                    return; // success, exit retry loop
                }

                System.out.println("Registration attempt " + attempt + " failed: HTTP "
                        + response.statusCode() + " - " + response.body());

            } catch (Exception e) {
                System.out.println("Registration attempt " + attempt + " error: " + e.getMessage());
            }

            if (attempt < maxAttempts) {
                System.out.println("Retrying in " + (delayMs / 1000) + "s...");
                Thread.sleep(delayMs);
            }
        }

        throw new RuntimeException("Registration failed after " + maxAttempts + " attempts. Is the backend running?");
    }

    private static void pollJob() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/jobs/poll/"+workerId))
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
            String fileUri = job.fileUrl;
            String fileName = fileUri.substring(fileUri.lastIndexOf("/")+1);
            System.out.println("Downloading dataset: "+fileName);
            jobDir = FileDownloader.download(job.fileUrl, job.jobId);

            System.out.println("Running container...");
//            String logs = DockerExecutor.runContainer(
//                    job.dockerImage,
//                    jobDir.toAbsolutePath().toString()
//            );

            DockerExecutor.ExecutionResult result = DockerExecutor.runContainer(job.dockerImage, jobDir.toString());

            Path outputDir = jobDir.resolve("output");
            if (Files.exists(outputDir)) {
                Path zip = ZipUtil.zipFolder(outputDir, job.jobId + ".zip");
                ArtifactUploader.upload(job.jobId, zip);
            } else {
                System.out.println("No output folder for job " + job.jobId);
            }
//            System.out.println("Uploading results..."+logs.length());
//            ResultUploader.upload(job.jobId, logs, DockerExecutor.runtimeMs);
            ResultUploader.upload(job.jobId, result.logs, result.runtimeMs);

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

    static public class ZipUtil {

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

//

    //using universal httpClient
    static class ArtifactUploader {

        static void upload(String jobId, Path zipPath) throws Exception {

            byte[] zipBytes = Files.readAllBytes(zipPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/jobs/artifact?jobId=" + jobId))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(zipBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request,  // ← shared client
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Artifact upload failed: HTTP " + response.statusCode());
            }

            System.out.println("Artifact uploaded for job: " + jobId);
        }
    }
}
