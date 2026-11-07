package bloodroute.server;

import bloodroute.ds.CustomLinkedList;
import bloodroute.model.*;
import bloodroute.service.BloodRouteService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight Embedded Web Server for BloodRoute using Java's built-in HttpServer.
 * Serves the interactive Leaflet OpenStreetMap web application and provides REST APIs.
 * Zero external libraries or frameworks required.
 */
public class BloodRouteWebServer {

    private final int port;
    private final BloodRouteService service;
    private HttpServer server;

    public BloodRouteWebServer(int port, BloodRouteService service) {
        this.port = port;
        this.service = service;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/network", new NetworkHandler());
        server.createContext("/api/donors", new DonorsHandler());
        server.createContext("/api/match", new MatchHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/route", new RouteHandler());

        // Static Web Files Handler
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // Default single/multithread executor
        server.start();
        System.out.println("==================================================================");
        System.out.println(" 🩸 BloodRoute Web Server is LIVE at: http://localhost:" + port);
        System.out.println(" Open this URL in your web browser to view the Bangladesh Emergency Map!");
        System.out.println("==================================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * GET /api/network - Returns all road network vertices and edges.
     */
    private class NetworkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"nodes\":[");
            LocationNode[] nodes = service.getCityGraph().getAllNodes();
            boolean firstNode = true;
            for (LocationNode node : nodes) {
                if (node != null) {
                    if (!firstNode) json.append(",");
                    json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"lat\":%.4f,\"lng\":%.4f,\"isHospital\":%b,\"area\":\"%s\",\"division\":\"%s\",\"contact\":\"%s\",\"address\":\"%s\",\"email\":\"%s\"}",
                            node.getId(), escapeJson(node.getName()), node.getLatitude(), node.getLongitude(), node.isHospital(),
                            escapeJson(node.getArea()), escapeJson(node.getDivision()), escapeJson(node.getContact()),
                            escapeJson(node.getAddress()), escapeJson(node.getEmail())));
                    firstNode = false;
                }
            }
            json.append("],\"edges\":[");

            boolean firstEdge = true;
            for (int i = 0; i < service.getCityGraph().getNumVertices(); i++) {
                LocationNode srcNode = service.getCityGraph().getNode(i);
                if (srcNode == null) continue;
                CustomLinkedList<Edge> edges = service.getCityGraph().getNeighbors(i);
                for (Edge e : edges) {
                    // Only output edge in one direction if src < dest to avoid duplicating undirected lines
                    if (i < e.getDestination()) {
                        if (!firstEdge) json.append(",");
                        json.append(String.format("{\"src\":%d,\"dest\":%d,\"distance\":%.2f,\"roadName\":\"%s\"}",
                                i, e.getDestination(), e.getDistanceKm(), escapeJson(e.getRoadName())));
                        firstEdge = false;
                    }
                }
            }
            json.append("]}");

            sendResponse(exchange, 200, json.toString(), "application/json");
        }
    }

    /**
     * GET /api/donors - Returns list of registered donors.
     */
    private class DonorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                new RegisterHandler().handle(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Donor d : service.getDonorRegistry()) {
                if (!first) json.append(",");
                json.append(donorToJson(d));
                first = false;
            }
            json.append("]");

            sendResponse(exchange, 200, json.toString(), "application/json");
        }
    }

    /**
     * POST /api/match - Finds and ranks nearest compatible donors.
     */
    private class MatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            int requesterNodeId = extractInt(body, "requesterNodeId", 0);
            String bloodTypeStr = extractString(body, "bloodType", "O+");

            BloodType requestedType = BloodType.fromString(bloodTypeStr);
            if (requestedType == null) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid blood type: " + bloodTypeStr + "\"}", "application/json");
                return;
            }

            try {
                bloodroute.ds.CustomLinkedList<bloodroute.algorithm.DijkstraRouter.SimulationStep> trace = new bloodroute.ds.CustomLinkedList<>();
                CustomLinkedList<MatchResult> matches = service.findNearestCompatibleDonors(requesterNodeId, requestedType, trace);

                StringBuilder json = new StringBuilder("{\"matches\":[");
                boolean first = true;
                for (MatchResult m : matches) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"donor\":").append(donorToJson(m.getDonor())).append(",");
                    json.append(String.format("\"roadDistanceKm\":%.2f,", m.getRoadDistanceKm()));
                    json.append(String.format("\"estimatedTimeMinutes\":%.1f,", m.getEstimatedTimeMinutes()));
                    json.append(String.format("\"isReachable\":%b,", m.isReachable()));
                    json.append("\"routePath\":[");
                    boolean firstP = true;
                    for (int p : m.getRoutePath()) {
                        if (!firstP) json.append(",");
                        json.append(p);
                        firstP = false;
                    }
                    json.append("]}");
                    first = false;
                }
                json.append("],\"simulationSteps\":[");
                boolean firstStep = true;
                for (bloodroute.algorithm.DijkstraRouter.SimulationStep s : trace) {
                    if (!firstStep) json.append(",");
                    json.append(String.format("{\"action\":\"%s\",\"u\":%d,\"v\":%d,\"distance\":%.2f,\"description\":\"%s\"}",
                            s.action, s.u, s.v, s.distance, escapeJson(s.description)));
                    firstStep = false;
                }
                json.append("]}");

                sendResponse(exchange, 200, json.toString(), "application/json");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", "application/json");
            }
        }
    }

    /**
     * POST /api/register - Adds new donor to the registry.
     */
    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            String id = extractString(body, "donorId", "D-" + (System.currentTimeMillis() % 10000));
            String name = extractString(body, "name", "");
            String bloodStr = extractString(body, "bloodType", "");
            int nodeId = extractInt(body, "locationNodeId", -1);
            String phone = extractString(body, "phoneNumber", "");
            if (phone.isEmpty()) {
                phone = extractString(body, "phone", "");
            }
            String address = extractString(body, "address", "");
            boolean available = extractBoolean(body, "isAvailable", true);

            BloodType bType = BloodType.fromString(bloodStr);
            if (bType == null || name.isEmpty() || nodeId < 0 || phone.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing or invalid fields for donor registration.\"}", "application/json");
                return;
            }

            try {
                Donor newDonor = new Donor(id, name, bType, nodeId, phone, available, address);
                service.registerDonor(newDonor);
                sendResponse(exchange, 201, "{\"success\":true,\"message\":\"Donor registered successfully!\",\"donor\":{\"id\":\"" + id + "\"}}", "application/json");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}", "application/json");
            }
        }
    }

    /**
     * GET /api/route?startLat=...&startLon=...&endLat=...&endLon=...
     * Calls OpenStreetMap's Open Source Routing Machine (OSRM) to calculate actual street-level geometry,
     * distance in meters, duration in seconds, and turn-by-turn navigation steps.
     */
    private class RouteHandler implements HttpHandler {
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS preflight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed. Use GET.\"}", "application/json");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            if (query == null || query.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing query parameters: startLat, startLon, endLat, endLon required.\"}", "application/json");
                return;
            }

            Map<String, String> params = parseQuery(query);
            double startLat, startLon, endLat, endLon;
            try {
                startLat = Double.parseDouble(params.get("startLat"));
                startLon = Double.parseDouble(params.get("startLon"));
                endLat = Double.parseDouble(params.get("endLat"));
                endLon = Double.parseDouble(params.get("endLon"));
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid coordinates format: must be valid numeric lat/lon coordinates.\"}", "application/json");
                return;
            }

            // Geographic boundary validation
            if (startLat < -90 || startLat > 90 || endLat < -90 || endLat > 90 ||
                startLon < -180 || startLon > 180 || endLon < -180 || endLon > 180) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Coordinates out of valid range (-90..90 for lat, -180..180 for lon).\"}", "application/json");
                return;
            }

            // If start and end are identical (e.g. on-site donor stationed at the hospital)
            if (Math.abs(startLat - endLat) < 0.00005 && Math.abs(startLon - endLon) < 0.00005) {
                String selfJson = String.format(java.util.Locale.US,
                        "{\"status\":\"success\",\"provider\":\"OSRM (On-Site)\",\"distanceMeters\":0.0,\"distanceKm\":0.0,\"durationSeconds\":0.0,\"durationMinutes\":0.0," +
                        "\"isOnSite\":true,\"raw\":{\"code\":\"Ok\",\"routes\":[{\"distance\":0.0,\"duration\":0.0,\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[%.6f,%.6f],[%.6f,%.6f]]},\"legs\":[{\"steps\":[],\"distance\":0.0,\"duration\":0.0}]}]}}",
                        startLon, startLat, endLon, endLat);
                sendResponse(exchange, 200, selfJson, "application/json");
                return;
            }

            // Construct OSRM Driving URL (Longitude first, then Latitude)
            String osrmUrl = String.format(java.util.Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                    startLon, startLat, endLon, endLat);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(osrmUrl))
                        .header("User-Agent", "BloodRoute-Emergency-System/2.0 (DSA Bangladesh Healthcare Proximity)")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    String osrmBody = response.body();
                    if (osrmBody.contains("\"code\":\"Ok\"") && osrmBody.contains("\"routes\":[")) {
                        sendResponse(exchange, 200, buildSuccessRouteJson(osrmBody), "application/json");
                    } else if (osrmBody.contains("\"code\":\"NoRoute\"")) {
                        sendResponse(exchange, 200, "{\"status\":\"no_route\",\"message\":\"No road connection found between points in OpenStreetMap.\",\"provider\":\"OSRM\",\"fallback\":true}", "application/json");
                    } else {
                        sendResponse(exchange, 200, "{\"status\":\"error\",\"message\":\"OSRM returned code without routes.\",\"provider\":\"OSRM\",\"fallback\":true}", "application/json");
                    }
                } else {
                    sendResponse(exchange, 502, "{\"status\":\"error\",\"message\":\"OSRM routing server returned HTTP " + response.statusCode() + "\",\"provider\":\"OSRM\",\"fallback\":true}", "application/json");
                }
            } catch (HttpTimeoutException te) {
                sendResponse(exchange, 504, "{\"status\":\"error\",\"message\":\"OSRM routing service timed out (8s limit).\",\"provider\":\"OSRM\",\"fallback\":true}", "application/json");
            } catch (Exception ex) {
                sendResponse(exchange, 502, "{\"status\":\"error\",\"message\":\"Could not contact OSRM: " + escapeJson(ex.getMessage()) + "\",\"provider\":\"OSRM\",\"fallback\":true}", "application/json");
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> map = new HashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0 && idx < pair.length() - 1) {
                    map.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
            return map;
        }

        private String buildSuccessRouteJson(String osrmRaw) {
            double distanceMeters = 0.0;
            double durationSeconds = 0.0;

            // Restrict search to route object before "waypoints" to extract total route metrics instead of intermediate steps
            int waypointsIdx = osrmRaw.indexOf("\"waypoints\"");
            String routeSection = waypointsIdx > 0 ? osrmRaw.substring(0, waypointsIdx) : osrmRaw;

            java.util.regex.Matcher distMatcher = java.util.regex.Pattern.compile("\"distance\"\\s*:\\s*([0-9.]+)").matcher(routeSection);
            while (distMatcher.find()) {
                try { distanceMeters = Double.parseDouble(distMatcher.group(1)); } catch (Exception ignored) {}
            }

            java.util.regex.Matcher durMatcher = java.util.regex.Pattern.compile("\"duration\"\\s*:\\s*([0-9.]+)").matcher(routeSection);
            while (durMatcher.find()) {
                try { durationSeconds = Double.parseDouble(durMatcher.group(1)); } catch (Exception ignored) {}
            }

            double distanceKm = distanceMeters / 1000.0;
            double durationMinutes = durationSeconds / 60.0;

            return String.format(java.util.Locale.US,
                    "{\"status\":\"success\",\"provider\":\"OSRM (OpenStreetMap)\",\"distanceMeters\":%.2f,\"distanceKm\":%.2f,\"durationSeconds\":%.2f,\"durationMinutes\":%.2f,\"raw\":%s}",
                    distanceMeters, distanceKm, durationSeconds, durationMinutes, osrmRaw);
        }
    }

    /**
     * Serves static web frontend files (HTML, CSS, JS).
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Map to web directory
            Path filePath = Paths.get("web" + path);
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(exchange, 404, "404 Not Found: " + path, "text/plain");
                return;
            }

            String mimeType = "text/plain";
            if (path.endsWith(".html")) mimeType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) mimeType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) mimeType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png")) mimeType = "image/png";
            else if (path.endsWith(".json")) mimeType = "application/json";

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String donorToJson(Donor d) {
        LocationNode node = service.getCityGraph().getNode(d.getLocationNodeId());
        String area = node != null ? node.getArea() : "Unknown";
        String division = node != null ? node.getDivision() : "Unknown";
        return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"bloodType\":\"%s\",\"nodeId\":%d,\"area\":\"%s\",\"division\":\"%s\",\"phone\":\"%s\",\"isAvailable\":%b,\"address\":\"%s\"}",
                escapeJson(d.getDonorId()), escapeJson(d.getName()), d.getBloodType().getLabel(),
                d.getLocationNodeId(), escapeJson(area), escapeJson(division), escapeJson(d.getPhoneNumber()), d.isAvailable(), escapeJson(d.getAddress()));
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    // Lightweight JSON parser helpers for basic POST payloads without external JSON library
    private String extractString(String json, String key, String defaultVal) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : defaultVal;
    }

    private int extractInt(String json, String key, int defaultVal) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private boolean extractBoolean(String json, String key, boolean defaultVal) {
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : defaultVal;
    }
}
