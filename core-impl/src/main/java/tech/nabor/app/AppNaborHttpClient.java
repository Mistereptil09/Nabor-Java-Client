package tech.nabor.app;

import tech.nabor.api.NaborHttpClient;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AppNaborHttpClient implements NaborHttpClient {

    private static final String BASE_URL = "http://localhost:3000/v1";

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    private volatile String token;

    public void setToken(String token) {
        this.token = token;
    }

    private java.net.http.HttpRequest.Builder base(String endpoint) {
        var b = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    @Override
    public String get(String endpoint) throws IOException {
        var request = base(endpoint).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    @Override
    public String post(String endpoint, String jsonBody) throws IOException {
        var request = base(endpoint).POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    @Override
    public String put(String endpoint, String jsonBody) throws IOException {
        var request = base(endpoint).PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    @Override
    public String delete(String endpoint) throws IOException {
        var request = base(endpoint).DELETE().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
}
