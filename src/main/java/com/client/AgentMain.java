package com.client;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    private static String workerSecret;

    public static void main(String[] args) throws Exception {

        LogCapture.install();

        System.out.println("Agent starting...");

        AgentMain agent = new AgentMain();

        try {
            pingServer();
            agent.register();
            agent.syncRates();
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return;
        }

//        DockerExecutor.runContainer("hello-world");

        while (true) {
            try {
                HeartbeatService.send(workerId, workerSecret, "IDLE");
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


    //register method wrt MAC+HOSTNAME + added validation and retry logic
    private void register() throws Exception {

        WorkerInfo info = SystemInfo.getWorkerInfo();
        this.workerId = info.workerId;
        this.workerSecret = info.workerSecret;

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
                .uri(URI.create(BASE_URL + "/jobs/poll/" + workerId
                        + "?workerSecret=" + workerSecret))
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

            // After DockerExecutor.runContainer()
            DockerExecutor.ExecutionResult result = DockerExecutor.runContainer(
                    job.dockerImage,
                    jobDir.toString(),
                    job.maxRuntimeSeconds,
                    job.requiredCpu,
                    job.requiredMemoryMB
            );

            if (result.timedOut) {
                // Upload whatever output exists
                Path outputDir = jobDir.resolve("output");
                if (Files.exists(outputDir)) {
                    Path zip = ZipUtil.zipFolder(outputDir, job.jobId + "_partial.zip");
                    ArtifactUploader.upload(job.jobId, zip);
                }

                // Report timeout to backend
                ResultUploader.uploadTimeout(job.jobId, result.runtimeMs, result.logs, workerSecret);

            } else {
                // Normal success flow
                Path outputDir = jobDir.resolve("output");
                if (Files.exists(outputDir)) {
                    Path zip = ZipUtil.zipFolder(outputDir, job.jobId + ".zip");
                    ArtifactUploader.upload(job.jobId, zip);
                }
                ResultUploader.upload(job.jobId, result.logs, result.runtimeMs, workerSecret);
            }

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
                .uri(URI.create(BASE_URL + "/jobs/fail?jobId=" + jobId
                        + "&workerSecret=" + workerSecret))
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
                    .uri(URI.create(BASE_URL + "/jobs/artifact?jobId=" + jobId
                            + "&workerSecret=" + workerSecret))
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
    private void syncRates() throws Exception {

        BigDecimal[] rates = SystemInfo.loadRates();

        String json = mapper.writeValueAsString(Map.of(
                "cpuRatePerSecond", rates[0],
                "gpuRatePerSecond", rates[1]
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/workers/rate?workerId=" + workerId
                        + "&workerSecret=" + workerSecret))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("Rates synced: cpu=$" + rates[0] + "/s gpu=$" + rates[1] + "/s");
        } else {
            System.out.println("Rate sync failed: HTTP " + response.statusCode());
        }
    }
}
