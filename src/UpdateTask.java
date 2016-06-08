import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Task that sends the update to the Code::Stats servers.
 */
public class UpdateTask implements Runnable {

    private Hashtable<String, Integer> xps;
    private Object xpsLock;
    private String apiURL;
    private String apiKey;
    private Hashtable<Project, StatusBarIcon> statusBarIcons;

    @Override
    public void run() {
        statusBarIcons.values().forEach(StatusBarIcon::setUpdating);

        try {
            // Sometimes after some combination of user action, the API URL ends up as an empty string, guard against it
            if (apiURL == null || apiURL.equals("")) {
                apiURL = SettingsForm.DEFAULT_API_URL;
            }

            final URL API_URL = new URL(apiURL);
            final String API_TOKEN = apiKey;

            HttpURLConnection conn = (HttpURLConnection) API_URL.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Token", API_TOKEN);

            Iterator<Map.Entry<String, Integer>> iter = xps.entrySet().iterator();

            JsonArray xps_json = (JsonArray) Json.array();
            while (iter.hasNext()) {
                Map.Entry<String, Integer> xp = iter.next();
                xps_json.add(Json.object().add("language", xp.getKey()).add("xp", xp.getValue()));
            }

            final String now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());

            JsonObject output = Json.object();
            output.add("coded_at", now);
            output.add("xps", xps_json);

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());

            writer.write(output.toString());
            writer.flush();

            if (conn.getResponseCode() == 201) {
                synchronized (xpsLock) {
                    xps.clear();
                }

                statusBarIcons.values().forEach(StatusBarIcon::clear);
            }

        } catch (IOException e) {
            e.printStackTrace();
            for (StatusBarIcon statusBarIcon : statusBarIcons.values()) {
                statusBarIcon.setError(e.toString());
            }
        }
    }

    public void setXps(Hashtable<String, Integer> xps) {
        this.xps = xps;
    }

    public void setXpsLock(final Object xpsLock) {
        this.xpsLock = xpsLock;
    }

    public void setConfig(final String apiURL, final String apiKey) {
        this.apiURL = apiURL;
        this.apiKey = apiKey;
    }

    public void setStatusBarIcons(Hashtable<Project, StatusBarIcon> statusBarIcons) {
        this.statusBarIcons = statusBarIcons;
    }
}
