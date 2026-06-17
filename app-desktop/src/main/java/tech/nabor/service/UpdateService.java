package tech.nabor.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.nabor.api.NaborHttpClient;
import tech.nabor.app.AppNaborHttpClient;

public class UpdateService {

    public static final String CURRENT_VERSION = loadCurrentVersion();

    private static String loadCurrentVersion() {
        String sysVer = System.getProperty("nabor.app.version");
        if (sysVer != null && !sysVer.isBlank()) {
            return sysVer;
        }
        String implVer = UpdateService.class.getPackage().getImplementationVersion();
        if (implVer != null && !implVer.isBlank()) {
            return implVer;
        }
        try (var is = UpdateService.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                var props = new java.util.Properties();
                props.load(is);
                String pVer = props.getProperty("version");
                if (pVer != null && !pVer.isBlank()) {
                    return pVer;
                }
            }
        } catch (Exception ignored) {}
        return "0.9.8-SNAPSHOT";
    }


    public record UpdateInfo(boolean available, String latestVersion, String sha256, String changelogUrl, String downloadUrl) {}

    private final NaborHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateService(NaborHttpClient http) {
        this.http = http;
    }

    /**
     * Checks if a new update is available on the server or raw URL.
     */
    public UpdateInfo checkForUpdate() {
        try {
            String json;
            String checkUrl = System.getProperty("nabor.update.checkUrl", "/updates/latest");
            System.out.println("[UpdateService] [HTTP Request] Checking for updates at: GET " + checkUrl);
            if (checkUrl.startsWith("http://") || checkUrl.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(checkUrl))
                        .header("User-Agent", "Java-Nabor-Client-Updater")
                        .GET()
                        .build();
                System.out.println("[UpdateService] [HTTP Request] Headers: User-Agent=Java-Nabor-Client-Updater");
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("[UpdateService] [HTTP Response] Received status code: " + response.statusCode());
                json = response.body();
            } else {
                json = http.get(checkUrl);
            }

            System.out.println("[UpdateService] [HTTP Response] Body: " + json);
            JsonNode root = mapper.readTree(json);
            String latestVersion = root.path("version").asText(CURRENT_VERSION);
            String sha256 = root.path("sha256").asText("");
            String changelogUrl = root.path("changelog_url").asText("");
            String downloadUrl = root.path("download_url").asText("");
            
            System.out.println("[UpdateService] Parsed update metadata: latestVersion=\"" + latestVersion 
                    + "\", sha256=\"" + sha256 + "\", downloadUrl=\"" + downloadUrl + "\"");

            boolean available = isNewerVersion(CURRENT_VERSION, latestVersion);
            System.out.println("[UpdateService] Version check: current=\"" + CURRENT_VERSION 
                    + "\" vs latest=\"" + latestVersion + "\" -> updateAvailable=" + available);
            return new UpdateInfo(available, latestVersion, sha256, changelogUrl, downloadUrl);
        } catch (Exception e) {
            System.err.println("[UpdateService] [HTTP Error] Failed to check for updates: " + e.getMessage());
            e.printStackTrace();
            return new UpdateInfo(false, CURRENT_VERSION, "", null, null);
        }
    }

    /**
     * Downloads the latest update ZIP to a temporary file.
     */
    public File downloadUpdate(String downloadUrl) throws IOException, InterruptedException {
        String finalUrl = downloadUrl;
        if (finalUrl == null || finalUrl.isBlank()) {
            finalUrl = AppNaborHttpClient.BASE_URL + "/updates/download";
        }
        System.out.println("[UpdateService] [HTTP Request] Downloading update from: GET " + finalUrl);
        
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .header("User-Agent", "Java-Nabor-Client-Updater")
                .GET()
                .build();

        File tempFile = File.createTempFile("nabor-update-", ".zip.tmp");
        tempFile.deleteOnExit();

        System.out.println("[UpdateService] [HTTP Request] Writing download stream to: " + tempFile.getAbsolutePath());
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()));
        System.out.println("[UpdateService] [HTTP Response] Download status code: " + response.statusCode());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download update. Server returned HTTP " + response.statusCode());
        }

        System.out.println("[UpdateService] Update downloaded successfully (size: " + tempFile.length() + " bytes)");
        return tempFile;
    }

    /**
     * Verifies the SHA-256 integrity of the downloaded file.
     */
    public boolean verifyIntegrity(File file, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(expectedSha256);
        } catch (Exception e) {
            System.err.println("[UpdateService] Verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extracts and replaces the running app files and restarts the application.
     */
    public void applyUpdateAndRestart(File zipFile) throws Exception {
        File runningJar;
        try {
            runningJar = new File(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IOException("Cannot locate running JAR file path", e);
        }

        File installDir = runningJar.getParentFile();
        if (runningJar.getName().equals("libs") || runningJar.getName().equals("build")) {
            installDir = installDir.getParentFile();
        }

        if (!runningJar.getName().endsWith(".jar")) {
            System.out.println("[UpdateService] Running in IDE or non-JAR package. Skipping restart overwrite.");
            return;
        }

        File tempDir = new File(installDir, "nabor_update_temp");
        if (tempDir.exists()) {
            deleteDir(tempDir);
        }
        tempDir.mkdirs();

        // 1. Extract ZIP to temp update folder
        System.out.println("[UpdateService] Extracting update package...");
        unzip(zipFile, tempDir);

        String tempDirPath = tempDir.getAbsolutePath();
        String installDirPath = installDir.getAbsolutePath();
        String zipFilePath = zipFile.getAbsolutePath();

        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Windows: xcopy the temp update folder over the install directory, rmdir tempDir, launch new JAR
            command.addAll(List.of(
                "cmd.exe", "/c",
                "ping 127.0.0.1 -n 3 > nul && xcopy /y /e /q \"" + tempDirPath + "\\*\" \"" + installDirPath + "\" && rmdir /s /q \"" + tempDirPath + "\" && del /q \"" + zipFilePath + "\" && start \"\" /b java -jar \"" + installDirPath + "\\nabor-desktop.jar\""
            ));
        } else {
            // macOS / Linux: sleep, cp -rf the temp update folder over install directory, rm -rf tempDir, launch new JAR
            command.addAll(List.of(
                "sh", "-c",
                "sleep 2 && cp -rf \"" + tempDirPath + "\"/* \"" + installDirPath + "\" && rm -rf \"" + tempDirPath + "\" \"" + zipFilePath + "\" && java -jar \"" + installDirPath + "/nabor-desktop.jar\" &"
            ));
        }

        System.out.println("[UpdateService] Launching updater process and shutting down...");
        new ProcessBuilder(command).start();
        System.exit(0);
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                // Prevent Zip Slip: ensure resolved path stays within destDir
                if (!newFile.toPath().normalize().startsWith(destDir.toPath())) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    private static boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;
        String[] currParts = current.split("\\.");
        String[] latParts = latest.split("\\.");
        int length = Math.max(currParts.length, latParts.length);
        for (int i = 0; i < length; i++) {
            try {
                int currPart = i < currParts.length ? Integer.parseInt(currParts[i].replaceAll("[^0-9]", "")) : 0;
                int latPart = i < latParts.length ? Integer.parseInt(latParts[i].replaceAll("[^0-9]", "")) : 0;
                if (latPart > currPart) return true;
                if (currPart > latPart) return false;
            } catch (NumberFormatException e) {
                // Fallback to string comparison if not numeric
                int comp = (i < latParts.length ? latParts[i] : "").compareTo(i < currParts.length ? currParts[i] : "");
                if (comp > 0) return true;
                if (comp < 0) return false;
            }
        }
        return false;
    }
}
