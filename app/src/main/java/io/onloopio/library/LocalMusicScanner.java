package io.onloopio.library;

import android.media.MediaMetadataRetriever;
import io.onloopio.db.MetadataStore;
import io.onloopio.model.Song;
import io.onloopio.model.CacheKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Incremental recursive tag reads; a failed traversal never yields a replacement snapshot. */
public final class LocalMusicScanner {
    public interface Progress {void found(int count);}
    public List<MetadataStore.LocalEntry> scan(File root,Set<String> managed,List<MetadataStore.LocalEntry> previous,Progress progress)throws IOException {
        if(!root.isDirectory() || !root.canRead())throw new IOException("Music storage unavailable");
        Map<String,MetadataStore.LocalEntry> known=new HashMap<String,MetadataStore.LocalEntry>();for(MetadataStore.LocalEntry e:previous)known.put(e.song.localPath,e);
        List<MetadataStore.LocalEntry> result=new ArrayList<MetadataStore.LocalEntry>();visit(root,root,managed,known,result,progress,0);return result;
    }
    private void visit(File root,File dir,Set<String> managed,Map<String,MetadataStore.LocalEntry> known,List<MetadataStore.LocalEntry> result,Progress progress,int depth)throws IOException {
        if(Thread.currentThread().isInterrupted())throw new IOException("Scan cancelled");if(depth>24)throw new IOException("Too many nested music folders");
        File[] files=dir.listFiles();if(files==null)throw new IOException("Music folder unreadable; index retained");
        for(File file:files){if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))continue;
            if(file.getName().startsWith("."))continue;
            if(file.isDirectory())visit(root,file,managed,known,result,progress,depth+1);
            else if(file.isFile() && file.length()>=16 && MusicPaths.audio(file) && !managed.contains(file.getCanonicalPath())){
                MetadataStore.LocalEntry old=known.get(file.getCanonicalPath());result.add(old!=null && old.bytes==file.length() && old.modified==file.lastModified()?old:read(root,file));
                if(result.size()>50000)throw new IOException("Local library exceeds 50000 tracks");if(progress!=null)progress.found(result.size());
            }
        }
    }
    private static String value(MediaMetadataRetriever tags,int key){try{String v=tags.extractMetadata(key);return v==null?"":v.trim();}catch(RuntimeException missing){return "";}}
    private static int number(String value){try{String n=value.split("[/ -]",2)[0];return Math.max(0,Integer.parseInt(n));}catch(RuntimeException missing){return 0;}}
    private MetadataStore.LocalEntry read(File root,File file)throws IOException {
        String title="",artist="",album="",genre="";int duration=0,track=0,disc=0;MediaMetadataRetriever tags=new MediaMetadataRetriever();
        try{tags.setDataSource(file.getPath());title=value(tags,MediaMetadataRetriever.METADATA_KEY_TITLE);artist=value(tags,MediaMetadataRetriever.METADATA_KEY_ARTIST);album=value(tags,MediaMetadataRetriever.METADATA_KEY_ALBUM);genre=value(tags,MediaMetadataRetriever.METADATA_KEY_GENRE);track=number(value(tags,MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));disc=number(value(tags,MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));try{duration=(int)Math.min(Integer.MAX_VALUE,Long.parseLong(value(tags,MediaMetadataRetriever.METADATA_KEY_DURATION))/1000);}catch(RuntimeException missing){}}
        catch(RuntimeException unsupported){ }finally{tags.release();}
        String stem=file.getName().substring(0,file.getName().lastIndexOf('.'));if(title.length()==0){title=stem.replaceFirst("^\\d+(?:-\\d+)?\\s*[-._]\\s*","");if(title.trim().length()==0)title=stem;}if(track==0)track=number(stem);
        File albumDir=file.getParentFile();if(albumDir.getName().matches("(?i)(cd|disc|disk)[ -]*[0-9]+")){if(disc==0)disc=number(albumDir.getName().replaceAll("[^0-9]",""));albumDir=albumDir.getParentFile();}
        String base=root.getCanonicalPath();if(!albumDir.getCanonicalPath().equals(base)){
            File artistDir=albumDir.getParentFile();if(artistDir!=null && !artistDir.getCanonicalPath().equals(base)){if(album.length()==0)album=albumDir.getName();if(artist.length()==0)artist=artistDir.getName();}else if(artist.length()==0)artist=albumDir.getName();
        }
        if(artist.length()==0)artist="Unknown artist";if(album.length()==0)album="Unknown album";
        String path=file.getCanonicalPath();Song song=new Song("local:"+CacheKey.hash(path),title,artist,album,MusicPaths.extension(file),duration,"",track,"",genre,disc,"",path);
        return new MetadataStore.LocalEntry(song,file.length(),file.lastModified());
    }
}
