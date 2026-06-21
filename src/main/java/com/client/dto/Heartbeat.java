package com.client.dto;

public class Heartbeat {

    public String workerId;
    public String status;
    public String workerSecret;

    public Heartbeat(String workerId, String workerSecret, String status) {
        this.workerId = workerId;
        this.workerSecret = workerSecret;
        this.status = status;
    }
}
