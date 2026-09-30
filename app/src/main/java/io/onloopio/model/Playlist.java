package io.onloopio.model;

public final class Playlist {
    public final String id, name, changed;
    public final int songCount;
    public final long duration;
    public Playlist(String id, String name, String changed, int songCount, long duration) {
        this.id = id; this.name = name; this.changed = changed;
        this.songCount = songCount; this.duration = duration;
    }
}
