package com.client.dto;

public class WorkerInfo {

    public String workerId;
    public int cpuCores;
    public long memoryMB;
    public String os;
    public boolean hasGpu;

    public WorkerInfo(){}

    public WorkerInfo(String workerId, int cpuCores, long memoryMB, String os,  boolean hasGpu) {
        this.workerId = workerId;
        this.cpuCores = cpuCores;
        this.memoryMB = memoryMB;
        this.os = os;
        this.hasGpu = hasGpu;
    }
}
