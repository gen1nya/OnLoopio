package io.onloopio.config;

import android.content.Context;
import android.content.SharedPreferences;
import io.onloopio.api.ServerConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

/** API 17: credentials stay in MODE_PRIVATE storage and are excluded from backup. */
public final class ConfigStore {
    private final Context context;
    private final SharedPreferences preferences;
    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("server", Context.MODE_PRIVATE);
    }
    public ServerConfig load() {
        try { return new ServerConfig(preferences.getString("url", ""), preferences.getString("username", ""), preferences.getString("password", ""),preferences.getString("trusted_ca", "")); }
        catch (IllegalArgumentException missing) { return null; }
    }
    public void save(ServerConfig config) {
        if (!preferences.edit().putString("url", config.baseUrl).putString("username", config.username)
                .putString("password", config.password).putString("trusted_ca", config.trustedCaPem).commit()) throw new IllegalStateException("Cannot save settings.");
    }
    /** Private debug provisioning file, uploaded using run-as. Never accepts intent credentials. */
    public boolean importPrivateFile() {
        File file = new File(context.getFilesDir(), "onloopio-config.json");
        if (!file.isFile()) return false;
        try {
            if (file.length() > 32768) return false;
            FileInputStream input = new FileInputStream(file);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try { byte[] buffer = new byte[1024]; int n; while ((n = input.read(buffer)) != -1) {
                if (bytes.size() + n > 32768) return false; bytes.write(buffer, 0, n);
            } } finally { input.close(); }
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
            save(new ServerConfig(json.getString("url"), json.getString("username"), json.getString("password"), json.optString("trustedCa", "")));
            return true;
        } catch (Exception e) { return false; }
        finally { if (!file.delete()) android.util.Log.w("OnLoopio", "CONFIG_IMPORT_CLEANUP_FAILED"); }
    }
}
