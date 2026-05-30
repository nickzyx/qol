package megawalls.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;

final class UpdateCheckerService {

    private static final String RELEASES_API =
        "https://api.github.com/repos/nickzyx/qol/releases";
    private static final String RELEASES_PAGE =
        "https://github.com/nickzyx/qol/releases";
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 10000;

    private boolean checkStarted;

    void onClientTick(Minecraft minecraft, MegaWallsConfig config) {
        if (
            checkStarted ||
            minecraft == null ||
            minecraft.thePlayer == null ||
            config == null ||
            !config.updateCheckerEnabled
        ) {
            return;
        }

        checkStarted = true;
        Thread thread = new Thread(
            new Runnable() {
                @Override
                public void run() {
                    checkForUpdates();
                }
            },
            "qol-update-checker"
        );
        thread.setDaemon(true);
        thread.start();
    }

    private void checkForUpdates() {
        try {
            ReleaseInfo latestRelease = findLatestRelease();
            if (latestRelease == null) {
                return;
            }

            if (!isNewerVersion(latestRelease.version, MegaWallsMod.VERSION)) {
                return;
            }

            notifyUpdateAvailable(latestRelease);
        } catch (Exception exception) {
            return;
        }
    }

    private ReleaseInfo findLatestRelease() throws IOException {
        HttpURLConnection connection = openConnection(RELEASES_API);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            JsonArray releases = new JsonParser()
                .parse(readResponse(connection))
                .getAsJsonArray();
            for (JsonElement element : releases) {
                if (!element.isJsonObject()) {
                    continue;
                }

                ReleaseInfo releaseInfo = parseRelease(
                    element.getAsJsonObject()
                );
                if (releaseInfo != null) {
                    return releaseInfo;
                }
            }

            return null;
        } finally {
            connection.disconnect();
        }
    }

    private ReleaseInfo parseRelease(JsonObject release) {
        if (release == null || getBoolean(release, "draft")) {
            return null;
        }

        String tagName = getString(release, "tag_name");
        String htmlUrl = getString(release, "html_url");
        if (tagName == null || tagName.trim().isEmpty()) {
            return null;
        }

        return new ReleaseInfo(
            normalizeVersion(tagName),
            htmlUrl == null ? RELEASES_PAGE : htmlUrl
        );
    }

    private void notifyUpdateAvailable(ReleaseInfo releaseInfo) {
        ChatNotifier.info(
            "qol " +
                releaseInfo.version +
                " is available. Current: " +
                MegaWallsMod.VERSION
        );
        ChatNotifier.link("Open qol releases", releaseInfo.releaseUrl);
    }

    private HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
            url
        ).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty(
            "User-Agent",
            "qol/" + MegaWallsMod.VERSION
        );
        return connection;
    }

    private String readResponse(HttpURLConnection connection)
        throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), "UTF-8")
        );
        try {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            reader.close();
        }
    }

    private boolean isNewerVersion(String candidate, String current) {
        int[] candidateParts = parseVersion(candidate);
        int[] currentParts = parseVersion(current);
        int partCount = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < partCount; index++) {
            int candidatePart =
                index < candidateParts.length ? candidateParts[index] : 0;
            int currentPart =
                index < currentParts.length ? currentParts[index] : 0;
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    private int[] parseVersion(String version) {
        String normalizedVersion = normalizeVersion(version);
        String[] tokens = normalizedVersion.split("\\.");
        int[] parts = new int[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            parts[index] = parseVersionPart(tokens[index]);
        }
        return parts;
    }

    private int parseVersionPart(String token) {
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (value < '0' || value > '9') {
                break;
            }
            digits.append(value);
        }

        if (digits.length() == 0) {
            return 0;
        }

        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }

        String normalizedVersion = version.trim();
        if (normalizedVersion.startsWith("v")) {
            return normalizedVersion.substring(1);
        }
        return normalizedVersion;
    }

    private String getString(JsonObject object, String key) {
        if (
            object == null || !object.has(key) || object.get(key).isJsonNull()
        ) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private boolean getBoolean(JsonObject object, String key) {
        return (
            object != null &&
            object.has(key) &&
            !object.get(key).isJsonNull() &&
            object.get(key).getAsBoolean()
        );
    }

    private static final class ReleaseInfo {

        private final String version;
        private final String releaseUrl;

        private ReleaseInfo(String version, String releaseUrl) {
            this.version = version;
            this.releaseUrl = releaseUrl;
        }
    }
}
