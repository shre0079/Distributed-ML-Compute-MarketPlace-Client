package com.client.util;

import com.client.dto.WorkerInfo;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.UUID;

public class SystemInfo {
    public static WorkerInfo getWorkerInfo(){
        int cpu=Runtime.getRuntime().availableProcessors();

        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        long memoryBytes=osBean.getTotalMemorySize();
        long memoryMB=memoryBytes / (1024*1024);

        String os=System.getProperty("os.name");
        String workerId= UUID.randomUUID().toString();

        return  new WorkerInfo(workerId, cpu, memoryMB, os);
    }
}
