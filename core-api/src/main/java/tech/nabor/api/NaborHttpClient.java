package tech.nabor.api;

import java.io.IOException;

public interface NaborHttpClient {
    // the token is already injected, the plugin dosen't access it
    String get(String endpoint) throws IOException;
    String post(String endpoint, String jsonBody) throws IOException;
    String put(String endpoint, String jsonBody) throws IOException;
    String delete(String endpoint) throws IOException;
}