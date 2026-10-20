import android.app.Activity;
import android.media.MediaDrm;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class VideoDRMPlayerActivity extends Activity {

    private static final String TAG = "Widevine";
    private static final UUID WIDEVINE_UUID =
            UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed");

    private static final String LICENSE_URL =
            "https://your-provider.example/widevine/license";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Supply Base64-encoded Widevine PSSH extracted from your content.
        String psshBase64 = getIntent().getStringExtra("pssh_base64");

        new Thread(() -> acquireLicense(psshBase64)).start();
    }

    private void acquireLicense(String psshBase64) {
        MediaDrm drm = null;
        byte[] sessionId = null;

        try {
            byte[] pssh = Base64.decode(psshBase64, Base64.DEFAULT);

            drm = new MediaDrm(WIDEVINE_UUID);

            // Widevine-specific property: request L3 for this example.
            // Must be set before opening the session; may be unsupported.
            drm.setPropertyString("securityLevel", "L3");

            sessionId = drm.openSession();

            // 1. Android forwards this to Widevine to generate a challenge.
            MediaDrm.KeyRequest request = drm.getKeyRequest(
                    sessionId,
                    pssh,
                    "video/mp4",
                    MediaDrm.KEY_TYPE_STREAMING,
                    null
            );

            // 2. The application sends the challenge to the licence server.
            byte[] response = postLicense(request.getData());

            // 3. Android forwards the response to Widevine for processing.
            drm.provideKeyResponse(sessionId, response);

            Log.i(TAG, "Licence installed successfully");

            // Playback would use this same open DRM session.
            // This demonstration closes it in finally.

        } catch (Exception e) {
            Log.e(TAG, "DRM licence exchange failed", e);
        } finally {
            if (drm != null) {
                try {
                    if (sessionId != null) drm.closeSession(sessionId);
                } finally {
                    drm.release();
                }
            }
        }
    }

    private byte[] postLicense(byte[] challenge) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(LICENSE_URL).openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty(
                    "Content-Type", "application/octet-stream");

            // Add authentication headers required by your provider.
            // connection.setRequestProperty("Authorization", "Bearer ...");

            try (OutputStream out = connection.getOutputStream()) {
                out.write(challenge);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new java.io.IOException("Licence HTTP status: " + status);
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
