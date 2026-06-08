package com.client.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CleanUpUtil {

    public static void cleanup(Path dir) {

        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try {
            List<Path> paths = Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            for (Path path : paths) {

                boolean deleted = path.toFile().delete();

                if (!deleted) {
                    System.out.println("Warning: could not delete " + path
                            + " — may be locked or permission denied.");
                }
            }

            System.out.println("Cleaned up job directory: " + dir);

        } catch (Exception e) {
            System.out.println("Cleanup failed for " + dir + ": " + e.getMessage());
        }
    }
}