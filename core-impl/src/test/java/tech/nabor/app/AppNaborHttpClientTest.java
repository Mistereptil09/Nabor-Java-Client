package tech.nabor.app;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.error.NaborException;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class AppNaborHttpClientTest {

    private WireMockServer server;
    private AppNaborHttpClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());

        client = new AppNaborHttpClient(
                "http://localhost:" + server.port(),
                "test-token"
        );
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void get_returns_response_body() throws IOException {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        String result = client.get("/incidents");
        assertEquals("{\"data\": []}", result);
    }

    @Test
    void get_sends_auth_token() throws IOException {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        client.get("/incidents");

        verify(getRequestedFor(urlEqualTo("/incidents"))
                .withHeader("Authorization", equalTo("Bearer test-token")));
    }

    @Test
    void get_handles_endpoint_without_leading_slash() throws IOException {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        assertDoesNotThrow(() -> client.get("incidents"));
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void post_sends_json_body() throws IOException {
        String body = "{\"title\": \"Test\"}";

        stubFor(post(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(201).withBody(body)));

        String result = client.post("/incidents", body);
        assertEquals(body, result);

        verify(postRequestedFor(urlEqualTo("/incidents"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalTo(body)));
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Test
    void put_sends_json_body() throws IOException {
        String body = "{\"status\": \"resolved\"}";

        stubFor(put(urlEqualTo("/incidents/abc"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        String result = client.put("/incidents/abc", body);
        assertEquals(body, result);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns_empty_body_on_204() throws IOException {
        stubFor(delete(urlEqualTo("/incidents/abc"))
                .willReturn(aResponse().withStatus(204).withBody("")));

        String result = client.delete("/incidents/abc");
        assertEquals("", result);
    }

    // ── Erreurs HTTP ──────────────────────────────────────────────────────────

    @Test
    void get_throws_auth_error_on_401() {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(401)));

        NaborException ex = assertThrows(NaborException.class,
                () -> client.get("/incidents"));
        assertEquals(NaborException.Kind.AUTH_ERROR, ex.getKind());
    }

    @Test
    void get_throws_not_found_on_404() {
        stubFor(get(urlEqualTo("/incidents/xyz"))
                .willReturn(aResponse().withStatus(404)));

        NaborException ex = assertThrows(NaborException.class,
                () -> client.get("/incidents/xyz"));
        assertEquals(NaborException.Kind.NOT_FOUND, ex.getKind());
    }

    @Test
    void post_throws_validation_error_on_400() {
        stubFor(post(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(400).withBody("Champ manquant")));

        NaborException ex = assertThrows(NaborException.class,
                () -> client.post("/incidents", "{}"));
        assertEquals(NaborException.Kind.VALIDATION, ex.getKind());
    }

    @Test
    void get_throws_http_error_on_500() {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        NaborException ex = assertThrows(NaborException.class,
                () -> client.get("/incidents"));
        assertEquals(NaborException.Kind.HTTP_ERROR, ex.getKind());
    }

    // ── updateToken ───────────────────────────────────────────────────────────

    @Test
    void updateToken_uses_new_token_for_subsequent_requests() throws IOException {
        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        client.updateToken("new-token");
        client.get("/incidents");

        verify(getRequestedFor(urlEqualTo("/incidents"))
                .withHeader("Authorization", equalTo("Bearer new-token")));
    }

    // ── baseUrl ───────────────────────────────────────────────────────────────

    @Test
    void baseUrl_trailing_slash_is_normalized() throws IOException {
        AppNaborHttpClient clientWithSlash = new AppNaborHttpClient(
                "http://localhost:" + server.port() + "/",
                "token"
        );

        stubFor(get(urlEqualTo("/incidents"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        assertDoesNotThrow(() -> clientWithSlash.get("/incidents"));
    }
}