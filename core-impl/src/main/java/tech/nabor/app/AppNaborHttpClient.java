package tech.nabor.app;

import tech.nabor.api.NaborHttpClient;

import java.io.IOException;

public class AppNaborHttpClient implements NaborHttpClient {
    
    @Override
    public String get(String endpoint) throws IOException {
        System.out.println("[HttpClient] GET " + endpoint);
        return "{}";
    }

    @Override
    public String post(String endpoint, String jsonBody) throws IOException {
        System.out.println("[HttpClient] POST " + endpoint + " with body: " + jsonBody);
        return "{}";
    }

    @Override
    public String put(String endpoint, String jsonBody) throws IOException {
        System.out.println("[HttpClient] PUT " + endpoint + " with body: " + jsonBody);
        return "{}";
    }

    @Override
    public String delete(String endpoint) throws IOException {
        System.out.println("[HttpClient] DELETE " + endpoint);
        return "{}";
    }
}
