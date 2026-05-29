package tech.nabor.service;

import java.io.IOException;
import java.util.UUID;

import tech.nabor.api.NaborHttpClient;

/**
 * Service d'authentification SSO par QR code (§7.2 — Device Authorization Flow).
 *
 * <p>Flux nominal : l'app génère un défi (UUID), affiche le QR, puis sonde le
 * statut jusqu'à validation côté React, et récupère alors les tokens.</p>
 *
 * <p>⚠️ Le backend NestJS n'étant pas branché ici ({@code AppNaborHttpClient}
 * est un stub), {@link #pollStatus(String)} reste {@code PENDING} et
 * {@link #simulateDevLogin()} permet de poursuivre en développement. Les vrais
 * appels HTTP sont indiqués en TODO.</p>
 */
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

    /** Crée un nouveau défi QR (étape 1 du flux). */
    public QrChallenge newChallenge() {
        // TODO réel : POST /auth/sso/qr/generate -> { token_uuid, api_url, png }
        String uuid = UUID.randomUUID().toString();
        String payload = "{\"token_uuid\":\"" + uuid + "\",\"api_url\":\"" + DEFAULT_API_URL + "\"}";
        return new QrChallenge(uuid, DEFAULT_API_URL, payload);
    }

    /** Sonde le statut du défi (étape 2, appelé en boucle toutes les 2 s). */
    public Status pollStatus(String tokenUuid) {
        // TODO réel : GET /auth/sso/qr/{uuid}/status -> { status, access_token?, refresh_token? }
        try {
            http.get("/auth/sso/qr/" + tokenUuid + "/status");
        } catch (IOException e) {
            // En dev (stub), pas d'effet ; en réel, à reporter.
        }
        return Status.PENDING;
    }

    /** DEV uniquement : simule une validation côté React et renvoie une session. */
    public Session simulateDevLogin() {
        return new Session("dev-admin-001", "admin@nabor.tech", "admin",
                "dev-access-token", "dev-refresh-token");
    }
}
