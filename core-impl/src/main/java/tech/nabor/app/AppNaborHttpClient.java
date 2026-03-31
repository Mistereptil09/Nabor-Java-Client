// core-impl/src/main/java/tech/nabor/app/AppNaborHttpClient.java
package tech.nabor.app;

import tech.nabor.api.NaborHttpClient;
import tech.nabor.api.error.NaborException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AppNaborHttpClient implements NaborHttpClient {

    private final HttpClient client;
    private final String baseUrl;
    private volatile String authToken; // volatile — can be updated from another thread

    public AppNaborHttpClient(String baseUrl, String authToken) {
        this.baseUrl   = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.client    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // called after a JWT token renewal
    public void updateToken(String newToken) {
        this.authToken = newToken;
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    @Override
    public String get(String endpoint) throws IOException {
        HttpRequest request = baseRequest(endpoint).GET().build();
        return execute(request);
    }

    @Override
    public String post(String endpoint, String jsonBody) throws IOException {
        HttpRequest request = baseRequest(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return execute(request);
    }

    @Override
    public String put(String endpoint, String jsonBody) throws IOException {
        HttpRequest request = baseRequest(endpoint)
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return execute(request);
    }

    @Override
    public String delete(String endpoint) throws IOException {
        HttpRequest request = baseRequest(endpoint).DELETE().build();
        return execute(request);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private HttpRequest.Builder baseRequest(String endpoint) {
        String url = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + authToken);
    }

    private String execute(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return switch (response.statusCode()) {
                case 200, 201, 204 -> response.body();
                case 401 -> throw new NaborException(
                        NaborException.Kind.AUTH_ERROR,
                        "Token expiré ou invalide", null);
                case 404 -> throw new NaborException(
                        NaborException.Kind.NOT_FOUND,
                        "Ressource introuvable : " + request.uri().getPath(), null);
                case 400 -> throw new NaborException(
                        NaborException.Kind.VALIDATION,
                        "Requête invalide : " + response.body(), null);
                default -> throw new NaborException(
                        NaborException.Kind.HTTP_ERROR,
                        "Erreur HTTP " + response.statusCode() + " : " + response.body(), null);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requête interrompue", e);
        }
    }
}