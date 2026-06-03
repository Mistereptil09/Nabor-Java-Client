package tech.nabor.service;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.nabor.api.NaborHttpClient;

public class AuthService {

    // On ajoute scanUrl pour pouvoir le passer au bouton du navigateur
    public record QrChallenge(String tokenUuid, String apiUrl, String qrPayload, String scanUrl) {}

    public record Session(String userId, String email, String role,
                          String accessToken, String refreshToken) {}

    public enum Status { PENDING, VALIDATED, EXPIRED }

    // Wrapper pour renvoyer le statut ET la session en une fois
    public record PollResult(Status status, Session session) {}

    private static final String DEFAULT_API_URL = "http://localhost:3000";
    private final ObjectMapper mapper = new ObjectMapper();
    private final NaborHttpClient http;

    public AuthService(NaborHttpClient http) {
        this.http = http;
    }

    public QrChallenge newChallenge() throws IOException {
        // N'oublie pas d'adapter la route avec "/v1/" si ton AppNaborHttpClient ne l'ajoute plus
        String body = http.post("/v1/auth/sso/qr/generate", "{}");
        JsonNode root = mapper.readTree(body);

        String scanUrl = root.path("scan_url").asText();

        // On extrait l'UUID proprement, avec un fallback de sécurité
        String uuid = Arrays.stream(URI.create(scanUrl).getQuery().split("&"))
                .filter(p -> p.startsWith("token="))
                .map(p -> p.substring(6))
                .findFirst()
                .orElse(UUID.randomUUID().toString());

        return new QrChallenge(uuid, DEFAULT_API_URL, scanUrl, scanUrl);
    }

    public PollResult pollStatus(String tokenUuid) {
        try {
            String body = http.get("/v1/auth/sso/qr/" + tokenUuid + "/status");
            // ON AFFICHE LA RÉPONSE DU SERVEUR DANS LA CONSOLE :
            System.out.println("Réponse Polling : " + body);

            JsonNode root = mapper.readTree(body);
            String statusStr = root.path("status").asText();

            if ("validated".equalsIgnoreCase(statusStr)) {
                Session session = new Session(
                        root.path("user_id").asText(""),
                        root.path("email").asText(""),
                        root.path("role").asText("user"),
                        // ATTENTION : Vérifie que c'est bien "access_token" et pas "accessToken" dans le JSON
                        root.path("access_token").asText(""),
                        root.path("refresh_token").asText("")
                );
                System.out.println("Connexion réussie ! Token : " + session.accessToken());
                return new PollResult(Status.VALIDATED, session);
            } else if ("expired".equalsIgnoreCase(statusStr)) {
                return new PollResult(Status.EXPIRED, null);
            }
        } catch (Exception e) {
            // ON ARRÊTE D'IGNORER LES ERREURS :
            System.err.println("Erreur pendant le polling : " + e.getMessage());
        }
        return new PollResult(Status.PENDING, null);
    }
    public Session simulateDevLogin() {
        return new Session("dev-admin-001", "admin@nabor.tech", "admin",
                "dev-access-token", "dev-refresh-token");
    }
}