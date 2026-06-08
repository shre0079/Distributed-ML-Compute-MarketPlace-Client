package com.client.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DockerExecutor {

    public static long runtimeMs;

    public static String runContainer(String image, String folder) throws Exception{

        // Fix for Windows: Docker requires absolute path with forward slashes
        String dockerPath = Path.of(folder)
                .toAbsolutePath()
                .toString()
                .replace("\\", "/");

        ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "-v", folder + ":/app/data",
                image
        );

        pb.redirectErrorStream(true);

        Process pull = new ProcessBuilder("docker", "pull", image)
                .inheritIO()
                .start();

        pull.waitFor();

        long start = System.currentTimeMillis();

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder logs = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            logs.append(line).append("\n");
        }

        int exitCode = process.waitFor();

        long end = System.currentTimeMillis();
        runtimeMs = end - start;

        System.out.println("Container runtime: " + runtimeMs + " ms");

        if (exitCode != 0) {
            throw new RuntimeException("Container exited with " + exitCode);
        }

    // Clean value object to return both logs and runtime together
    public static class ExecutionResult {

        public final String logs;
        public final long runtimeMs;

        public ExecutionResult(String logs, long runtimeMs) {
            this.logs = logs;
            this.runtimeMs = runtimeMs;
        }
    }
}