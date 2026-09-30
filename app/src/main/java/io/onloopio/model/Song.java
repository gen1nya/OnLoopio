package io.onloopio.model;

public final class Song {
    public final String id, title, artist, album, suffix, albumId, artistId, genre, coverArt,localPath;
    public final int duration, track, disc;
    public Song(String id, String title, String artist, String album, String suffix, int duration) {
        this(id,title,artist,album,suffix,duration,"",0);
    }
    public Song(String id,String title,String artist,String album,String suffix,int duration,String albumId,int track) {
        this(id,title,artist,album,suffix,duration,albumId,track,"","",0);
    }
    public Song(String id,String title,String artist,String album,String suffix,int duration,String albumId,int track,String artistId,String genre,int disc) {
        this(id,title,artist,album,suffix,duration,albumId,track,artistId,genre,disc,"");
    }
    public Song(String id,String title,String artist,String album,String suffix,int duration,String albumId,int track,String artistId,String genre,int disc,String coverArt) {
        this(id,title,artist,album,suffix,duration,albumId,track,artistId,genre,disc,coverArt,"");
    }
    public Song(String id,String title,String artist,String album,String suffix,int duration,String albumId,int track,String artistId,String genre,int disc,String coverArt,String localPath) {
        this.id=id; this.title=title; this.artist=artist; this.album=album; this.suffix=suffix; this.duration=duration; this.albumId=albumId; this.track=track; this.artistId=artistId; this.genre=genre; this.disc=disc; this.coverArt=coverArt;this.localPath=localPath;
    }
    public boolean local(){return localPath.length()>0;}
    public String artistKey() { return artistId.length()>0?"id:"+artistId:"name:"+artist; }
    public String albumKey() { return albumId.length()>0?"id:"+albumId:"name:"+artist+"\u0000"+album; }
}
