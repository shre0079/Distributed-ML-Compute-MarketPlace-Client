package com.client.util;

public class FileDownloader {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static Path download(String url, String jobId) throws Exception {

        Path dir = Path.of("jobs",jobId);
        Files.createDirectories(dir);

        Path filePath = dir.resolve("input.txt");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());

        Files.copy(response.body(),filePath);

        return dir;
    }
}
