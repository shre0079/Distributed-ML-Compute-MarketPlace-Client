package com.client.util;

import com.client.dto.WorkerInfo;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

//MAC+HostName Hashed WorkerIDs
public class SystemInfo {

    public static WorkerInfo getWorkerInfo() {
        int cpu = Runtime.getRuntime().availableProcessors();
        long memoryMB = detectMemoryMB();
        String os = System.getProperty("os.name");
        String workerId = deriveWorkerId();
        boolean hasGpu = detectGpu();
        String workerSecret = getOrCreateWorkerSecret();

        return new WorkerInfo(workerId, cpu, memoryMB, os, hasGpu, workerSecret);
    }

    private static long detectMemoryMB() {

        try {
            // Try the Sun internal API first — most accurate, works on Oracle/OpenJDK
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                // Pattern matching instanceof — Java 16+
                // Only cast if it's actually available, no hard import dependency
                return sunBean.getTotalMemorySize() / (1024 * 1024);
            }

        } catch (Exception e) {
            System.out.println("Sun MXBean unavailable, falling back to Runtime memory detection.");
        }

        // Standard fallback — Runtime.maxMemory() reflects JVM heap limit
        // Not total physical RAM but a reasonable approximation for scheduling
        long runtimeMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        System.out.println("Using Runtime memory estimate: " + runtimeMemoryMB + " MB");
        return runtimeMemoryMB;
    }

    private static String deriveWorkerId() {

        try {
            String mac = "unknown-mac";

            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {

                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;

                byte[] macBytes = ni.getHardwareAddress();

                if (macBytes == null || macBytes.length == 0) continue;

                StringBuilder sb = new StringBuilder();
                for (byte b : macBytes) {
                    sb.append(String.format("%02x", b));
                }

                mac = sb.toString();
                break;
            }

            String hostname = InetAddress.getLocalHost().getHostName();
            String raw = mac + ":" + hostname;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hash = new StringBuilder();
            for (byte b : hashBytes) {
                hash.append(String.format("%02x", b));
                if (hash.length() >= 12) break;
            }

            String workerId = "worker-" + hash.toString();
            System.out.println("Derived workerId: " + workerId
                    + " (MAC=" + mac + " hostname=" + hostname + ")");
            return workerId;

        } catch (Exception e) {
            System.out.println("Warning: could not derive stable workerId, falling back to UUID.");
            return "worker-" + UUID.randomUUID().toString();
        }
    }

    private static boolean detectGpu() {

        try {
            Process process = new ProcessBuilder("nvidia-smi", "-L")
                    .redirectErrorStream(true)
                    .start();

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("GPU detected via nvidia-smi.");
                return true;
            }

        } catch (Exception e) {
            // nvidia-smi not found — no NVIDIA GPU
        }

        System.out.println("No GPU detected, registering as CPU-only worker.");
        return false;
    }

    private static final Path WORKER_SECRET_FILE = Path.of("worker.secret");

    private static String getOrCreateWorkerSecret() {
        try {
            if (Files.exists(WORKER_SECRET_FILE)) {
                String secret = Files.readString(WORKER_SECRET_FILE).trim();
                System.out.println("Loaded existing workerSecret.");
                return secret;
            }

            // First run — generate and persist
            String secret = UUID.randomUUID().toString()
                    .replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            Files.writeString(WORKER_SECRET_FILE, secret);
            System.out.println("Generated new workerSecret.");
            return secret;

        } catch (Exception e) {
            System.out.println("Warning: could not persist workerSecret.");
            return UUID.randomUUID().toString();
        }
    }
}