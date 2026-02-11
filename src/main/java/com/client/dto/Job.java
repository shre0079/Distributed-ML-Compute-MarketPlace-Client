package com.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

    public String dockerImage;
    public String jobId;
    public String fileUrl;
}
