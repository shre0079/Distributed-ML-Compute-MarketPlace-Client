package com.client.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DockerExecutor {

    public static String runContainer(String image, String folder) throws Exception{

        ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "-v", folder + ":/app/data",
                image
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder logs = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            logs.append(line).append("\n");
        }

        process.waitFor();

        return logs.toString();
    }
}
