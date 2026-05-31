package tech.nabor.service;

import java.io.IOException;

import tech.nabor.api.NaborHttpClient;

public class UpdateService {

    public static final String CURRENT_VERSION = "1.0.0";

    public record UpdateInfo(boolean available, String latestVersion, String changelogUrl) {}

    private final NaborHttpClient http;

    public UpdateService(NaborHttpClient http) {
        this.http = http;
    }

   
    public UpdateInfo checkForUpdate() {
        try {
            http.get("/updates/latest");
        } catch (IOException e) {
        }
        return new UpdateInfo(false, CURRENT_VERSION, null);
    }
}
