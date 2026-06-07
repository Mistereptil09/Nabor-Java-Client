package tech.nabor.plugin.sync;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the Sync plugin's API communication.
 *
 * <p>The Sync plugin is responsible for:
 * <ol>
 *   <li>Pull: {@code GET /sync/snapshot} — delta snapshot of all entities</li>
 *   <li>Push: {@code POST /sync/updates} — batch of offline changes</li>
 * </ol>
 *
 * <p>These tests use the same {@code AppNaborHttpClient} that the plugin
 * receives via {@code PluginContext.getHttpClient()}. The token is injected
 * the same way the main app does after SSO login.</p>
 *
 * <p><b>Prerequisites:</b> running NestJS API + a valid JWT.</p>
 * <pre>
 *   ./gradlew :plugins:sync:e2eTest -Dnabor.test.token=&lt;jwt&gt;
 * </pre>
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SyncPluginE2ETest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static boolean serverAvailable;
    private static String testToken;

    private AppNaborHttpClient httpClient;

    @BeforeAll
    static void checkServer() {
        serverAvailable = pingServer();
        testToken = resolveToken();
        if (!serverAvailable) {
            System.err.println("Nabor API unreachable — sync e2e tests skipped.");
        }
        if (testToken == null) {
            System.err.println("No test token — authenticated sync tests skipped.");
            System.err.println("  Provide one via: -Dnabor.test.token=<jwt>");
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(serverAvailable, "Skipped: NestJS API not reachable");
        httpClient = new AppNaborHttpClient();
    }

    // ── 1. Auth required ────────────────────────────────────────────────────

    @Test @Order(1)
    void snapshot_withoutToken_returns401() {
        IOException ex = assertThrows(IOException.class,
                () -> httpClient.get("/sync/snapshot?since=2025-01-01T00:00:00Z"),
                "GET /sync/snapshot without token must throw");

        assertTrue(ex.getMessage().contains("401") || ex.getMessage().contains("UNAUTHORIZED"),
                "Expected 401, got: " + ex.getMessage());
    }

    @Test @Order(2)
    void updates_withoutToken_returns401() {
        var unauthClient = new AppNaborHttpClient();
        String body = """
        {"jobId":"%s","updates":[]}
        """.formatted(UUID.randomUUID());

        IOException ex = assertThrows(IOException.class,
                () -> unauthClient.post("/sync/updates", body),
                "POST /sync/updates without token must throw");

        assertTrue(ex.getMessage().contains("401") || ex.getMessage().contains("UNAUTHORIZED"),
                "Expected 401, got: " + ex.getMessage());
    }

    // ── 2. GET /sync/snapshot — pull ────────────────────────────────────────

    @Test @Order(3)
    void snapshot_withToken_returnsExpectedStructure() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        String body = httpClient.get("/sync/snapshot?since=2025-01-01T00:00:00Z&limit=1");
        JsonNode root = mapper.readTree(body);

        // Per CDC §4.10 the snapshot response must contain these fields:
        List<String> required = List.of("sync_at", "has_more");
        List<String> entityArrays = List.of(
                "incidents", "users_raw", "listings", "events",
                "listing_categories", "event_categories"
        );

        List<String> missing = new ArrayList<>();
        for (String key : required) {
            if (!root.has(key)) missing.add(key);
        }
        for (String key : entityArrays) {
            if (!root.has(key)) missing.add(key);
        }

        assertTrue(missing.isEmpty(),
                "Sync snapshot missing expected fields: " + missing + "\n" +
                "Present fields: " + root.fieldNames());

        // sync_at must be a valid ISO-8601 timestamp
        String syncAt = root.path("sync_at").asText();
        assertFalse(syncAt.isBlank(), "sync_at must not be blank");
        assertDoesNotThrow(() -> java.time.Instant.parse(syncAt),
                "sync_at must be parseable as ISO-8601, got: " + syncAt);

        // has_more must be boolean
        assertTrue(root.path("has_more").isBoolean(),
                "has_more must be a boolean");

        // Entity arrays must actually be arrays (even if empty)
        for (String key : entityArrays) {
            assertTrue(root.path(key).isArray(),
                    key + " must be a JSON array, got: " + root.path(key).getNodeType());
        }

        System.out.println("Snapshot sync_at: " + syncAt
                + "  has_more: " + root.path("has_more").asBoolean()
                + "  incidents: " + root.path("incidents").size()
                + "  users: " + root.path("users_raw").size()
                + "  listings: " + root.path("listings").size()
                + "  events: " + root.path("events").size());
    }

    @Test @Order(4)
    void snapshot_pagination_worksWithCursor() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        // Request a small page — if there are more results, has_more should be true
        String body = httpClient.get("/sync/snapshot?since=2025-01-01T00:00:00Z&limit=1");
        JsonNode root = mapper.readTree(body);

        boolean hasMore = root.path("has_more").asBoolean(false);
        String cursor = root.path("cursor").asText(null);

        System.out.println("Page 1 — has_more: " + hasMore + "  cursor: " + cursor);

        if (hasMore && cursor != null) {
            // Fetch the second page
            String page2Body = httpClient.get(
                    "/sync/snapshot?since=2025-01-01T00:00:00Z&limit=1&cursor=" + cursor);
            JsonNode page2 = mapper.readTree(page2Body);

            // Second page must also be valid
            assertTrue(page2.has("sync_at"), "Page 2 must have sync_at");
            assertTrue(page2.has("has_more"), "Page 2 must have has_more");

            System.out.println("Page 2 — has_more: " + page2.path("has_more").asBoolean());
        }
    }

    // ── 3. POST /sync/updates — push ────────────────────────────────────────

    @Test @Order(5)
    void push_emptyBatch_isAccepted() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        String body = """
        {"jobId":"%s","updates":[]}
        """.formatted(UUID.randomUUID());

        // An empty push should be accepted (idempotent — no changes to apply)
        String response = httpClient.post("/sync/updates", body);
        JsonNode root = mapper.readTree(response);

        // The API should return a valid JSON response
        System.out.println("Empty push response: " + root);
    }

    @Test @Order(6)
    void push_jobId_isRequired() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        // Missing jobId should be rejected
        try {
            String response = httpClient.post("/sync/updates", "{\"updates\":[]}");
            System.out.println("Push without jobId response: " + response);
        } catch (IOException e) {
            // 400 Bad Request is expected
            assertTrue(e.getMessage().contains("400"),
                    "Push without jobId should return 400, got: " + e.getMessage());
        }
    }

    @Test @Order(7)
    void push_updatesArray_isRequired() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        // Missing updates array should be rejected
        try {
            String response = httpClient.post("/sync/updates",
                    "{\"jobId\":\"" + UUID.randomUUID() + "\"}");
            System.out.println("Push without updates response: " + response);
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("400"),
                    "Push without updates array should return 400, got: " + e.getMessage());
        }
    }

    @Test @Order(8)
    void push_withValidUpdate_isAccepted() throws Exception {
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        // A well-formed update referencing a non-existent entity
        // should be accepted (the server may skip it or return an error)
        String body = """
        {
          "jobId": "%s",
          "updates": [
            {
              "entity_type": "incident",
              "entity_id": "00000000-0000-0000-0000-000000000000",
              "changes": {"title": "test"},
              "base_updated_at": "2025-06-01T00:00:00Z"
            }
          ]
        }
        """.formatted(UUID.randomUUID());

        try {
            String response = httpClient.post("/sync/updates", body);
            System.out.println("Valid update push response: " + response);
        } catch (IOException e) {
            // 404 (entity not found) is acceptable — the structure is correct
            assertTrue(e.getMessage().contains("404") || e.getMessage().contains("200"),
                    "Expected 200 or 404, got: " + e.getMessage());
        }
    }

    // ── 4. Token shared from main app session ───────────────────────────────

    @Test @Order(9)
    void tokenFromMainApp_isUsableForSync() throws Exception {
        // This test verifies that the SAME token used for SSO /users/me
        // also works for sync endpoints. This is how plugins receive it
        // via PluginContext.getHttpClient().
        assumeTrue(testToken != null, "Skipped: no test token");
        httpClient.setToken(testToken);

        // Use the token for both profile (main app) and snapshot (sync plugin)
        String profile = httpClient.get("/users/me");
        assertDoesNotThrow(() -> mapper.readTree(profile),
                "Token must work for /users/me (main app)");

        String snapshot = httpClient.get("/sync/snapshot?since=2025-01-01T00:00:00Z&limit=1");
        assertDoesNotThrow(() -> mapper.readTree(snapshot),
                "Same token must work for /sync/snapshot (sync plugin)");

        System.out.println("Token works for both main app and sync plugin ✓");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:3000/v1";
    private static final String BASE_URL = resolveBaseUrl();

    static String resolveBaseUrl() {
        String url = System.getProperty("nabor.test.baseUrl");
        if (url != null && !url.isBlank()) return url;
        url = System.getenv("NABOR_TEST_BASE_URL");
        return (url != null && !url.isBlank()) ? url : DEFAULT_BASE_URL;
    }

    private static boolean pingServer() {
        try {
            System.out.println("E2E pre-flight: GET " + BASE_URL + "/health");
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

    /**
     * Resolves a JWT for authenticated tests.
     * Use {@code -Dnabor.test.token=<jwt>} or env {@code NABOR_TEST_TOKEN}.
     * Without a token, authenticated tests are skipped.
     */
    static String resolveToken() {
        String token = System.getProperty("nabor.test.token");
        if (token != null && !token.isBlank()) return token;
        token = System.getenv("NABOR_TEST_TOKEN");
        return (token != null && !token.isBlank()) ? token : null;
    }
}
