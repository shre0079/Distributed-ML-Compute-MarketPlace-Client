package com.client.dto;

public class WorkerInfo {

    public String workerId;
    public int cpuCores;
    public long memoryMB;
    public String os;
    public boolean hasGpu;
    public String workerSecret;

    public WorkerInfo(String workerId, int cpuCores, long memoryMB, String os, boolean hasGpu, String workerSecret) {
        this.workerId = workerId;
        this.cpuCores = cpuCores;
        this.memoryMB = memoryMB;
        this.os = os;
        this.hasGpu = hasGpu;
        this.workerSecret = workerSecret;
    }
}
