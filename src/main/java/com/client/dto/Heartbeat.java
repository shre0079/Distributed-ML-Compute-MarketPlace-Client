package com.client.dto;

public class Heartbeat {

    public String workerId;
    public String status;

    public Heartbeat(String workerId, String status){
        this.workerId = workerId;
        this.status = status;
    }
}
