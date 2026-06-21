package com.client.dto;

import java.math.BigDecimal;

public class WorkerInfo {

    public String workerId;
    public int cpuCores;
    public long memoryMB;
    public String os;
    public boolean hasGpu;
    public String workerSecret;
    public BigDecimal cpuRatePerSecond;
    public BigDecimal gpuRatePerSecond;


    public WorkerInfo(String workerId, int cpuCores, long memoryMB, String os, boolean hasGpu, String workerSecret, BigDecimal cpuRatePerSecond, BigDecimal gpuRatePerSecond) {
        this.workerId = workerId;
        this.cpuCores = cpuCores;
        this.memoryMB = memoryMB;
        this.os = os;
        this.hasGpu = hasGpu;
        this.workerSecret = workerSecret;
        this.cpuRatePerSecond = cpuRatePerSecond;
        this.gpuRatePerSecond = gpuRatePerSecond;
    }
}
