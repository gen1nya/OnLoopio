package io.onloopio;

import android.test.InstrumentationTestCase;
import io.onloopio.api.NavidromeClient;
import io.onloopio.api.ServerConfig;
import io.onloopio.config.ConfigStore;

/** Optional read-only real-server proof on the provisioned API-17 emulator/Y1. */
public final class ConnectionTest extends InstrumentationTestCase {
    public void testPrivateCaIsRequired() throws Exception {
        ServerConfig config = new ConfigStore(getInstrumentation().getTargetContext()).load();
        if (config == null || config.trustedCaPem.length() == 0 || !config.baseUrl.startsWith("https://")) return;
        NavidromeClient untrusted = new NavidromeClient(new ServerConfig(config.baseUrl,config.username,config.password));
        try { untrusted.ping(); fail("Private CA connection succeeded without its trust anchor."); }
        catch (javax.net.ssl.SSLException expected) { /* chain verification remains enabled */ }
    }
    public void testConfiguredServer() {
        ServerConfig config = new ConfigStore(getInstrumentation().getTargetContext()).load();
        if (config == null) return;
        try {
            NavidromeClient client = new NavidromeClient(config);
            client.ping();
            java.util.List<io.onloopio.model.Playlist> playlists = client.getPlaylists();
            android.util.Log.i("OnLoopioTest", "LIVE_PLAYLISTS count=" + playlists.size());
            if (!playlists.isEmpty()) {
                int count = client.getPlaylist(playlists.get(0).id).songs.size();
                android.util.Log.i("OnLoopioTest", "LIVE_PLAYLIST_ENTRIES count=" + count);
            }
        } catch (Exception e) {
            Throwable cause = e;
            StringBuilder message = new StringBuilder();
            while (cause != null) {
                message.append(cause.getClass().getSimpleName()).append(" ");
                String detail = cause.getMessage();
                // Test diagnostics may name TLS causes, never print URLs, credential parameters or arbitrary server text.
                if (detail != null && !detail.contains("://") && !detail.contains("u=") && !detail.contains("t=") && !detail.contains("s="))
                    message.append(detail.substring(0,Math.min(detail.length(),200))).append("; ");
                cause = cause.getCause();
            }
            fail(message.toString());
        }
    }
}
