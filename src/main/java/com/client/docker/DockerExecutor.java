package com.client.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DockerExecutor {

    public static void runContainer(String image) throws Exception{

        ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                image
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();

        System.out.println("Container exited with code : " + exitCode);
    }
}
