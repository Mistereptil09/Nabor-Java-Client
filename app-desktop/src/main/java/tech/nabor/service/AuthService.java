package tech.nabor.service;

import java.io.IOException;
import java.util.UUID;

import tech.nabor.api.NaborHttpClient;


public class AuthService {

    public record QrChallenge(String tokenUuid, String apiUrl, String qrPayload) {}

    public record Session(String userId, String email, String role,
                          String accessToken, String refreshToken) {}

    public enum Status { PENDING, VALIDATED, EXPIRED }

    private static final String DEFAULT_API_URL = "http://localhost:3000";

    private final NaborHttpClient http;

    public AuthService(NaborHttpClient http) {
        this.http = http;
    }

    public QrChallenge newChallenge() {
        // TODO réel : POST /auth/sso/qr/generate -> { token_uuid, api_url, png }
        String uuid = UUID.randomUUID().toString();
        String payload = "{\"token_uuid\":\"" + uuid + "\",\"api_url\":\"" + DEFAULT_API_URL + "\"}";
        return new QrChallenge(uuid, DEFAULT_API_URL, payload);
    }

    public Status pollStatus(String tokenUuid) {
        // TODO réel : GET /auth/sso/qr/{uuid}/status -> { status, access_token?, refresh_token? }
        try {
            http.get("/auth/sso/qr/" + tokenUuid + "/status");
        } catch (IOException e) {
        }
        return Status.PENDING;
    }

    public Session simulateDevLogin() {
        return new Session("dev-admin-001", "admin@nabor.tech", "admin",
                "dev-access-token", "dev-refresh-token");
    }
}
