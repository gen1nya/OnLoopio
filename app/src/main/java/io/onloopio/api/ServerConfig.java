package io.onloopio.api;

import java.net.URI;

public final class ServerConfig {
    public final String baseUrl, username, password, trustedCaPem;
    public ServerConfig(String baseUrl, String username, String password) {
        this(baseUrl, username, password, "");
    }
    public ServerConfig(String baseUrl, String username, String password, String trustedCaPem) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            URI uri = new URI(normalized);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) ||
                    uri.getHost() == null || uri.getUserInfo() != null ||
                    uri.getQuery() != null || uri.getFragment() != null) throw new Exception();
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Enter an HTTP(S) server URL without credentials or query parameters.");
        }
        if (username == null || username.trim().length() == 0 || password == null || password.length() == 0)
            throw new IllegalArgumentException("Username and password are required.");
        this.baseUrl = normalized; this.username = username.trim(); this.password = password;
        if (trustedCaPem != null && trustedCaPem.length() > 16384) throw new IllegalArgumentException("CA certificate is too large.");
        this.trustedCaPem = trustedCaPem == null ? "" : trustedCaPem;
    }
    public String accountKey() { return baseUrl + "\n" + username; }
}
