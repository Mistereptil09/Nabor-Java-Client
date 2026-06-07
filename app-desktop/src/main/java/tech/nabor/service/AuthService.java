package tech.nabor.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.nabor.api.NaborHttpClient;
import tech.nabor.app.AppNaborHttpClient;

public class AuthService {

    public record QrChallenge(String tokenUuid, String apiUrl, String qrPayload, String scanUrl) {}

    public record Session(String accessToken, String refreshToken) {
        public Session(String accessToken) { this(accessToken, null); }
    }

    public enum Status { PENDING, VALIDATED, EXPIRED }

    public record PollResult(Status status, Session session) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final NaborHttpClient http;

    public AuthService(NaborHttpClient http) {
        this.http = http;
    }

    public QrChallenge newChallenge() throws IOException {
        String body = http.post("/auth/sso/qr/generate", "{}");
        JsonNode root = mapper.readTree(body);

        String scanUrl = root.path("scan_url").asText();

        String uuid = Arrays.stream(URI.create(scanUrl).getQuery().split("&"))
                .filter(p -> p.startsWith("token="))
                .map(p -> p.substring(6))
                .findFirst()
                .orElse(UUID.randomUUID().toString());

        return new QrChallenge(uuid, scanUrl, scanUrl, scanUrl);
    }

    public PollResult pollStatus(String tokenUuid) {
        try {
            String body = http.get("/auth/sso/qr/" + tokenUuid + "/status");
            System.out.println("Polling response: " + body);

            JsonNode root = mapper.readTree(body);
            String statusStr = root.path("status").asText();

            if ("validated".equalsIgnoreCase(statusStr)) {
                String accessToken = root.path("access_token").asText("");
                String refreshToken = root.path("refresh_token").asText(null);
                Session session = new Session(accessToken, refreshToken);
                System.out.println("Login successful — access_token: "
                        + (accessToken.length() > 20 ? accessToken.substring(0, 20) + "..." : accessToken));
                return new PollResult(Status.VALIDATED, session);
            } else if ("expired".equalsIgnoreCase(statusStr)) {
                return new PollResult(Status.EXPIRED, null);
            }
        } catch (Exception e) {
            System.err.println("Polling error: " + e.getMessage());
        }
        return new PollResult(Status.PENDING, null);
    }

    /**
     * Refreshes the access token using a refresh token.
     * Sends the refresh token in the Authorization header.
     * POST /auth/refresh → { access_token, refresh_token }
     */
    public Session refresh(String refreshToken) throws IOException {
        var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var req = HttpRequest.newBuilder()
                .uri(URI.create(AppNaborHttpClient.BASE_URL + "/auth/refresh"))
                .header("Authorization", "Bearer " + refreshToken)
                .header("Accept", "application/json")
                .header("User-Agent", "Java-Nabor-Client")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IOException("Refresh failed: HTTP " + resp.statusCode());
            }
            JsonNode root = mapper.readTree(resp.body());
            String newAccess = root.path("access_token").asText("");
            String newRefresh = root.path("refresh_token").asText(refreshToken);
            return new Session(newAccess, newRefresh);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Refresh interrupted", e);
        }
    }

    public Session simulateDevLogin() {
        return new Session("dev-access-token");
    }
}