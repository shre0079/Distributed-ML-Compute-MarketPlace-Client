package com.client.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class DockerExecutor {

    public static ExecutionResult runContainer(String image,
                                               String folder,
                                               int maxRuntimeSeconds,
                                               int cpuCores,
                                               long memoryMB) throws Exception {

        String dockerPath = Path.of(folder)
                .toAbsolutePath()
                .toString()
                .replace("\\", "/");

        // Pull image first — stream through System.out so it reaches
        // the dashboard's live log even with no console attached
        Process pull = new ProcessBuilder("docker", "pull", image)
                .redirectErrorStream(true)
                .start();

        try (BufferedReader pullReader = new BufferedReader(
                new InputStreamReader(pull.getInputStream()))) {
            String pullLine;
            while ((pullLine = pullReader.readLine()) != null) {
                System.out.println("[pull] " + pullLine);
            }
        }
        pull.waitFor();


        // step 2 --- build run cmd with resource limits
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--cpus=" + cpuCores,                    // ← CPU limit
                "--memory=" + memoryMB + "m",            // ← memory limit
                "--memory-swap=" + memoryMB + "m",       // ← disable swap
                "-v", dockerPath + ":/app/data",
                image
        );
        pb.redirectErrorStream(true);

        //step 3 --- start timing after pull
        long start = System.currentTimeMillis();
        Process process = pb.start();


        //step 4 --- capture output
        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.append(line).append("\n");
                System.out.println("[docker] " + line);
            }
        }

        // Wait up to maxRuntimeSeconds --- step 5
        boolean finishedInTime = process.waitFor(
                maxRuntimeSeconds, TimeUnit.SECONDS);

        long runtimeMs = System.currentTimeMillis() - start;

        if (!finishedInTime) {
            // SIGTERM first — give script 30 seconds to save checkpoint
            System.out.println("Job exceeded maxRuntimeSeconds=" +
                    maxRuntimeSeconds + "s, sending SIGTERM...");
            process.destroy();

            boolean cleanExit = process.waitFor(30, TimeUnit.SECONDS);

            if (!cleanExit) {
                // SIGKILL if still alive after grace period
                System.out.println("Grace period expired, sending SIGKILL...");
                process.destroyForcibly();
            }

            return new ExecutionResult(logs.toString(), runtimeMs, true); // true = timed out
        }

        int exitCode = (int) process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Container exited with code: " + exitCode);
        }

        return new ExecutionResult(logs.toString(), runtimeMs, false);
    }


    // Clean value object to return both logs and runtime together
    public static class ExecutionResult {
        public final String logs;
        public final long runtimeMs;
        public final boolean timedOut;    // ← new

        public ExecutionResult(String logs, long runtimeMs, boolean timedOut) {
            this.logs = logs;
            this.runtimeMs = runtimeMs;
            this.timedOut = timedOut;
        }
    }
}