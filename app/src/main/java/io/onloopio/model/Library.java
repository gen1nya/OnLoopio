package io.onloopio.model;
import java.util.ArrayList;
import java.util.List;
/** ID3 catalog, including entities with no playlist membership. */
public final class Library {
    public static final class Entity {
        public final String id,name,artistId,artist;
        public Entity(String id,String name,String artistId,String artist) { this.id=id; this.name=name; this.artistId=artistId; this.artist=artist; }
    }
    public final List<Entity> artists=new ArrayList<Entity>(),albums=new ArrayList<Entity>();
    public final List<Song> songs=new ArrayList<Song>();
}
