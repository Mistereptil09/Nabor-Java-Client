package tech.nabor.app;

import tech.nabor.api.EventBus;
import tech.nabor.api.NaborHttpClient;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpClient;

public class AppNaborHttpClient implements NaborHttpClient {

    @FunctionalInterface
    public interface TokenRefresher {
        String refresh() throws IOException;
    }

    /** Event published when a network error occurs. Payload = error message. */
    public static final String NETWORK_ERROR = "network.error";

    public static final String BASE_URL = resolveBaseUrl();

    private static String resolveBaseUrl() {
        String url = System.getProperty("nabor.api.baseUrl");
        if (url != null && !url.isBlank()) return url;
        url = System.getenv("NABOR_API_BASE_URL");
        return (url != null && !url.isBlank()) ? url : "http://127.0.0.1:3000/v1";
    }

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private volatile String token;
    private volatile TokenRefresher tokenRefresher;
    private volatile EventBus eventBus;

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    /** Set a callback that will be invoked on 401 to attempt a token refresh. */
    public void setTokenRefresher(TokenRefresher refresher) {
        this.tokenRefresher = refresher;
    }

    private HttpRequest.Builder base(String endpoint) {
        var b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Java-Nabor-Client");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    @Override
    public String get(String endpoint) throws IOException {
        return sendWithRetry(() -> base(endpoint).GET().build());
    }

    @Override
    public String post(String endpoint, String jsonBody) throws IOException {
        return sendWithRetry(() ->
                base(endpoint).POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build());
    }

    @Override
    public String put(String endpoint, String jsonBody) throws IOException {
        return sendWithRetry(() ->
                base(endpoint).PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build());
    }

    @Override
    public String delete(String endpoint) throws IOException {
        return sendWithRetry(() -> base(endpoint).DELETE().build());
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }

    /**
     * Sends the request. On 401, attempts a single token refresh via
     * {@link #tokenRefresher} and retries once.
     */
    private String sendWithRetry(java.util.function.Supplier<HttpRequest> requestBuilder)
            throws IOException {
        try {
            return sendRequest(requestBuilder.get());
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHORIZED")
                    && tokenRefresher != null) {
                try {
                    String newToken = tokenRefresher.refresh();
                    this.token = newToken;
                    // Retry once with the new token
                    return sendRequest(requestBuilder.get());
                } catch (IOException refreshError) {
                    throw new IOException("Token refresh failed: " + refreshError.getMessage(), refreshError);
                }
            }
            throw e;
        }
    }

    private String sendRequest(HttpRequest request) throws IOException {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                var msg = "Session expired — please reconnect.";
                publishError(msg);
                throw new IOException("UNAUTHORIZED: " + msg);
            }
            if (response.statusCode() >= 400) {
                var msg = "HTTP " + response.statusCode() + " on " + request.uri();
                publishError(msg);
                throw new IOException(msg + " : " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            // Only publish if not already published above (401/4xx/5xx)
            if (e.getMessage() == null || !e.getMessage().startsWith("HTTP ")
                    && !e.getMessage().startsWith("UNAUTHORIZED")) {
                publishError("Network error: " + e.getMessage());
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publishError("Request interrupted");
            throw new IOException("Requête interrompue", e);
        }
    }

    private void publishError(String msg) {
        System.err.println("[HTTP] " + msg);
        if (eventBus != null) {
            try { eventBus.publish(NETWORK_ERROR, msg); }
            catch (Exception ignored) {}
        }
    }
}