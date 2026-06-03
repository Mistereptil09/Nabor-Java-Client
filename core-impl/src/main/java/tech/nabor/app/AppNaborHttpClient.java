package tech.nabor.app;

import tech.nabor.api.NaborHttpClient;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpClient;

public class AppNaborHttpClient implements NaborHttpClient {

    // 1. On utilise 127.0.0.1 au lieu de localhost pour contourner le bug IPv6 de Node.js
    // 2. On retire le "/v1" ici, car ton AuthService l'inclut déjà ("/v1/auth/sso...")
    private static final String BASE_URL = "http://127.0.0.1:3000";

    // 3. On force le HTTP/1.1
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private volatile String token;

    public void setToken(String token) {
        this.token = token;
    }

    private HttpRequest.Builder base(String endpoint) {
        var b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json") // <-- Rassure NestJS sur le format attendu
                .header("User-Agent", "Java-Nabor-Client"); // <-- Évite les blocages de sécurité
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    @Override
    public String get(String endpoint) throws IOException {
        var request = base(endpoint).GET().build();
        return sendRequest(request);
    }

    @Override
    public String post(String endpoint, String jsonBody) throws IOException {
        var request = base(endpoint).POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return sendRequest(request);
    }

    @Override
    public String put(String endpoint, String jsonBody) throws IOException {
        var request = base(endpoint).PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return sendRequest(request);
    }

    @Override
    public String delete(String endpoint) throws IOException {
        var request = base(endpoint).DELETE().build();
        return sendRequest(request);
    }

    // Petite méthode utilitaire pour éviter de répéter le try/catch 4 fois
    private String sendRequest(HttpRequest request) throws IOException {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                // Si l'URL est mauvaise (404) ou que le serveur plante (500), ça remontera ici
                throw new IOException("HTTP " + response.statusCode() + " : " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requête interrompue", e);
        }
    }
}