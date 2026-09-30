package io.onloopio.api;

import java.io.IOException;

public final class ApiException extends IOException {
    public final int code;
    public ApiException(int code) {
        super(code == 40 || code == 41 ? "Authentication failed. Check your account settings." :
              code == 50 ? "This account cannot access the requested resource." :
              code == 70 ? "Playlist no longer exists on the server." : "Server API error (" + code + ").");
        this.code = code;
    }
}
