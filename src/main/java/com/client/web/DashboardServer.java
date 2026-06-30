package com.client.web;

import com.client.util.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DashboardServer {

    private static final String BACKEND_URL = "http://localhost:8080";
    private static final int PORT = 9090;

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void start() {

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            server.createContext("/", DashboardServer::serveStatic);
            server.createContext("/api/status", DashboardServer::handleStatus);
            server.createContext("/api/jobs", DashboardServer::handleJobs);
            server.createContext("/api/withdrawals", DashboardServer::handleWithdrawals);
            server.createContext("/api/rate", DashboardServer::handleRateUpdate);
            server.createContext("/api/withdraw", DashboardServer::handleWithdraw);
            server.createContext("/api/logs", DashboardServer::handleLogs);

            server.setExecutor(null);
            server.start();

            System.out.println("Dashboard running at http://localhost:" + PORT);

        } catch (IOException e) {
            System.out.println("Could not start local dashboard: " + e.getMessage());
        }
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {

        String path = exchange.getRequestURI().getPath();

//        if (path.equals("/")) path = "/dashboard/index.html";

        if (path.equals("/")) {
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        InputStream in = DashboardServer.class.getResourceAsStream("/dashboard" + path);

        if (in == null) {
            sendText(exchange, 404, "Not found");
            return;
        }

        byte[] bytes = in.readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", guessContentType(path));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    private static void handleStatus(HttpExchange exchange) throws IOException {

        if (AgentState.workerId == null) {
            sendJson(exchange, 503, "{\"error\":\"Agent has not registered with the backend yet.\"}");
            return;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/workers/" + AgentState.workerId))
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                sendJson(exchange, resp.statusCode(), resp.body());
                return;
            }

            Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
            data.put("currentJobId", AgentState.currentJobId);
            data.put("currentJobStatus", AgentState.currentJobStatus);

            sendJson(exchange, 200, mapper.writeValueAsString(data));

        } catch (Exception e) {
            sendJson(exchange, 502, "{\"error\":\"Could not reach the backend.\"}");
        }
    }

    private static void handleJobs(HttpExchange exchange) throws IOException {
        proxyGet(exchange, "/workers/" + AgentState.workerId + "/jobs?workerSecret=" + AgentState.workerSecret);
    }

    private static void handleWithdrawals(HttpExchange exchange) throws IOException {
        proxyGet(exchange, "/workers/" + AgentState.workerId + "/withdrawals?workerSecret=" + AgentState.workerSecret);
    }

    private static void handleRateUpdate(HttpExchange exchange) throws IOException {

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/workers/rate?workerId=" + AgentState.workerId
                            + "&workerSecret=" + AgentState.workerSecret))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            sendJson(exchange, resp.statusCode(), resp.body());

        } catch (Exception e) {
            sendJson(exchange, 502, "{\"error\":\"Could not reach the backend.\"}");
        }
    }

    private static void handleWithdraw(HttpExchange exchange) throws IOException {

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + "/workers/withdraw?workerId=" + AgentState.workerId
                            + "&workerSecret=" + AgentState.workerSecret))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            sendJson(exchange, resp.statusCode(), resp.body());

        } catch (Exception e) {
            sendJson(exchange, 502, "{\"error\":\"Could not reach the backend.\"}");
        }
    }

    private static void handleLogs(HttpExchange exchange) throws IOException {
        String tail = LogCapture.tail(150);
        sendJson(exchange, 200, mapper.writeValueAsString(Map.of("logs", tail)));
    }

    private static void proxyGet(HttpExchange exchange, String path) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL + path))
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            sendJson(exchange, resp.statusCode(), resp.body());

        } catch (Exception e) {
            sendJson(exchange, 502, "{\"error\":\"Could not reach the backend.\"}");
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}