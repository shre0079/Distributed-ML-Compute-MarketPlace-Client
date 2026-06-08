package com.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class CleanUpUtil {

    public static void cleanup(Path dir) {

        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try {
            List<Path> paths = Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

        if (!Files.exists(dir)) return;

        Files.walk(dir)
                .sorted(Comparator.reverseOrder()) // delete files first, then folder
                .forEach(path -> path.toFile().delete());

        System.out.println("Cleaned up: " + dir);
    }
}
