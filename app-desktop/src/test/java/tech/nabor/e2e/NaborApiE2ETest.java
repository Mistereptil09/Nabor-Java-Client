package tech.nabor.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import tech.nabor.app.AppNaborHttpClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the main app's responsibility: SSO login flow,
 * user profile fetch, and token management.
 *
 * <p>The main Java Desktop client only handles:
 * <ol>
 *   <li>SSO QR code generation + polling (login)</li>
 *   <li>Fetching the authenticated user's profile</li>
 *   <li>Injecting the JWT into the HTTP client for plugins to use</li>
 * </ol>
 *
 * <p>All other API calls (sync, listings, events, categories, etc.)
 * are the responsibility of plugins — tested in {@code SyncPluginE2ETest}.</p>
 *
 * <p>Requires a running NestJS API. Tests are tagged {@code e2e} and
 * skip gracefully when the server is unreachable.</p>
 *
 * <pre>./gradlew :app-desktop:e2eTest</pre>
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NaborApiE2ETest {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:3000/v1";
    private static final String BASE_URL = resolveBaseUrl();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static boolean serverAvailable;

    private AppNaborHttpClient httpClient;

    @BeforeAll
    static void checkServer() {
        serverAvailable = pingServer();
        if (!serverAvailable) {
            System.err.println("Nabor API unreachable — e2e tests skipped. Start with: docker compose up -d");
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(serverAvailable, "Skipped: NestJS API not reachable");
        httpClient = new AppNaborHttpClient();
    }

    // ── 1. Connectivity ─────────────────────────────────────────────────────

    @Test @Order(1)
    void healthEndpoint_returns200() throws Exception {
        var resp = rawGet(BASE_URL + "/health");
        assertEquals(200, resp.statusCode(), "/health should return 200");
    }

    // ── 2. SSO QR login flow (the only auth path for the desktop client) ───

    @Test @Order(2)
    void ssoQrGenerate_returnsScanUrl() throws Exception {
        String body = httpClient.post("/auth/sso/qr/generate",
                "{\"device_name\": \"E2E Test Runner (Java Client)\"}");
        JsonNode root = mapper.readTree(body);

        String scanUrl = root.path("scan_url").asText();
        assertFalse(scanUrl.isBlank(), "QR generate must return a scan_url");
        assertTrue(scanUrl.contains("token="),
                "scan_url must contain token param, got: " + scanUrl);

        System.out.println("QR scan URL: " + scanUrl);
    }

    @Test @Order(3)
    void ssoQrPoll_pendingQr_returnsPendingStatus() throws Exception {
        // Generate a QR, then immediately poll — should be "pending"
        String genBody = httpClient.post("/auth/sso/qr/generate",
                "{\"device_name\": \"E2E Test Runner (Java Client)\"}");
        JsonNode genRoot = mapper.readTree(genBody);
        String tokenUuid = extractTokenUuid(genRoot.path("scan_url").asText());
        assumeTrue(tokenUuid != null, "Must extract token UUID from scan_url");

        String body = httpClient.get("/auth/sso/qr/" + tokenUuid + "/status");
        JsonNode root = mapper.readTree(body);
        String status = root.path("status").asText();

        assertTrue(java.util.Set.of("pending", "expired").contains(status),
                "QR should be pending or expired, got: " + status);

        // Token must NOT be leaked while QR is pending
        assertTrue(root.path("access_token").asText().isBlank(),
                "access_token must not be exposed while QR is pending");
    }

    @Test @Order(4)
    void ssoQrPoll_unknownUuid_returnsExpiredOr404() throws Exception {
        try {
            String body = httpClient.get("/auth/sso/qr/00000000-0000-0000-0000-000000000000/status");
            JsonNode root = mapper.readTree(body);
            String status = root.path("status").asText("unknown");
            assertTrue(java.util.Set.of("expired", "pending", "unknown").contains(status),
                    "Unknown QR UUID should return expired/pending, got: " + status);
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("404") || e.getMessage().contains("400"),
                    "Expected 404 for unknown QR UUID, got: " + e.getMessage());
        }
    }

    // ── 3. User profile (the only authenticated GET the main app performs) ──

    @Test @Order(5)
    void usersMe_withoutToken_returns401() {
        var unauthClient = new AppNaborHttpClient();
        IOException ex = assertThrows(IOException.class,
                () -> unauthClient.get("/users/me"),
                "GET /users/me without token must throw");

        assertTrue(ex.getMessage().contains("401") || ex.getMessage().contains("UNAUTHORIZED"),
                "Expected 401, got: " + ex.getMessage());
    }

    @Test @Order(6)
    void usersMe_withToken_returnsProfile() throws Exception {
        String token = resolveToken();
        assumeTrue(token != null, "Skipped: no test token — set -Dnabor.test.token=<jwt>");

        httpClient.setToken(token);
        String body = httpClient.get("/users/me");
        JsonNode profile = mapper.readTree(body);

        // The NaborApp.onAuthenticated() flow expects these fields:
        assertTrue(profile.has("id"), "Profile must have 'id'");
        assertTrue(profile.has("email"), "Profile must have 'email'");
        assertTrue(profile.has("role"), "Profile must have 'role'");
        assertTrue(profile.has("firstName"), "Profile must have 'firstName'");
        assertTrue(profile.has("lastName"), "Profile must have 'lastName'");

        System.out.println("User: " + profile.path("firstName").asText()
                + " " + profile.path("lastName").asText()
                + " (" + profile.path("role").asText() + ")");
    }

    // ── 4. Token state tracking (AppNaborHttpClient.isAuthenticated) ───────

    @Test @Order(7)
    void tokenState_freshClient_isNotAuthenticated() {
        assertFalse(httpClient.isAuthenticated(),
                "Fresh client with no token must not be authenticated");
    }

    @Test @Order(8)
    void tokenState_afterSetToken_isAuthenticated() {
        httpClient.setToken("some-jwt");
        assertTrue(httpClient.isAuthenticated());
    }

    @Test @Order(9)
    void tokenState_afterClear_isNotAuthenticated() {
        httpClient.setToken("some-jwt");
        assertTrue(httpClient.isAuthenticated());
        httpClient.setToken("");
        assertFalse(httpClient.isAuthenticated());
    }

    @Test @Order(10)
    void tokenState_nullToken_isNotAuthenticated() {
        httpClient.setToken(null);
        assertFalse(httpClient.isAuthenticated());
    }

    // ── 5. Auth endpoints the login screen may call ─────────────────────────

    @Test @Order(11)
    void forgotPassword_alwaysReturns200() throws Exception {
        // Per CDC: this route never reveals whether the email exists
        String body = httpClient.post("/auth/forgot-password",
                "{\"email\": \"test@nabor.tech\"}");
        mapper.readTree(body); // must be valid JSON
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Base URL for the API under test. Override with:
     * {@code -Dnabor.test.baseUrl=http://host:port/v1}
     */
    static String resolveBaseUrl() {
        String url = System.getProperty("nabor.test.baseUrl");
        if (url != null && !url.isBlank()) return url;
        url = System.getenv("NABOR_TEST_BASE_URL");
        return (url != null && !url.isBlank()) ? url : DEFAULT_BASE_URL;
    }

    private static boolean pingServer() {
        try {
            System.out.println("E2E pre-flight: GET " + BASE_URL + "/health");
            // Same workarounds as AppNaborHttpClient: force HTTP/1.1 to avoid
            // Node.js IPv6 bug, and set standard headers.
            var client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/health"))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Java-Nabor-E2E")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("E2E pre-flight: HTTP " + resp.statusCode() + " — API reachable");
            return true;
        } catch (Exception e) {
            System.err.println("E2E pre-flight FAILED on " + BASE_URL + "/health : " + e);
            return false;
        }
    }

    private static HttpResponse<String> rawGet(String url) throws Exception {
        var client = HttpClient.newHttpClient();
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Resolves a JWT for authenticated tests.
     * Use system property {@code -Dnabor.test.token=<jwt>} to provide one.
     * Without it, authenticated tests are skipped.
     */
    static String resolveToken() {
        String token = System.getProperty("nabor.test.token");
        if (token != null && !token.isBlank()) return token;
        // Fallback: try env variable
        token = System.getenv("NABOR_TEST_TOKEN");
        return (token != null && !token.isBlank()) ? token : null;
    }

    private static String extractTokenUuid(String scanUrl) {
        try {
            String query = URI.create(scanUrl).getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) return param.substring(6);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
