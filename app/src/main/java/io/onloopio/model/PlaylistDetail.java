package io.onloopio.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaylistDetail {
    public final Playlist playlist;
    public final List<Song> songs;
    public PlaylistDetail(Playlist playlist, List<Song> songs) {
        this.playlist = playlist;
        this.songs = Collections.unmodifiableList(new ArrayList<Song>(songs));
    }
}
