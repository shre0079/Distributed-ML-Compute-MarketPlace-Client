package com.client.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LogCapture extends OutputStream {

    private static final int MAX_LINES = 500;
    private static final ConcurrentLinkedDeque<String> lines = new ConcurrentLinkedDeque<>();
    private final StringBuilder currentLine = new StringBuilder();
    private final OutputStream original;

    private LogCapture(OutputStream original) {
        this.original = original;
    }

    // Call once at the very start of main(). Mirrors every System.out print
    // into a bounded in-memory buffer the dashboard reads, while still
    // printing to the real console when one exists (dev mode).
    public static void install() {
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new LogCapture(originalOut), true, StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void write(int b) throws IOException {
        original.write(b);
        char c = (char) b;
        if (c == '\n') {
            lines.addLast(currentLine.toString());
            currentLine.setLength(0);
            while (lines.size() > MAX_LINES) {
                lines.pollFirst();
            }
        } else if (c != '\r') {
            currentLine.append(c);
        }
    }

    public static String tail(int maxLines) {
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, lines.size() - maxLines);
        int i = 0;
        for (String line : lines) {
            if (i++ < skip) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}