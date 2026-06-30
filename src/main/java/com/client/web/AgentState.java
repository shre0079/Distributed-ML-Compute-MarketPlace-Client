package com.client.web;

public class AgentState {
    public static volatile String workerId;
    public static volatile String workerSecret;
    public static volatile String currentJobId;
    public static volatile String currentJobStatus = "IDLE"; // IDLE or RUNNING
}