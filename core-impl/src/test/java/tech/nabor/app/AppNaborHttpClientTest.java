package tech.nabor.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppNaborHttpClientTest {

    @Test
    void isAuthenticated_returnsFalse_whenTokenNeverSet() {
        var client = new AppNaborHttpClient();
        assertFalse(client.isAuthenticated());
    }

    @Test
    void isAuthenticated_returnsFalse_whenTokenIsNull() {
        var client = new AppNaborHttpClient();
        client.setToken(null);
        assertFalse(client.isAuthenticated());
    }

    @Test
    void isAuthenticated_returnsFalse_whenTokenIsBlank() {
        var client = new AppNaborHttpClient();
        client.setToken("   ");
        assertFalse(client.isAuthenticated());
    }

    @Test
    void isAuthenticated_returnsTrue_afterSetToken() {
        var client = new AppNaborHttpClient();
        client.setToken("valid-access-token");
        assertTrue(client.isAuthenticated());
    }

    @Test
    void setToken_canBeUpdated() {
        var client = new AppNaborHttpClient();
        client.setToken("first-token");
        assertTrue(client.isAuthenticated());

        client.setToken("second-token");
        assertTrue(client.isAuthenticated());
    }

    @Test
    void setToken_clearMakesUnauthenticated() {
        var client = new AppNaborHttpClient();
        client.setToken("some-token");
        assertTrue(client.isAuthenticated());

        client.setToken("");
        assertFalse(client.isAuthenticated());
    }
}
